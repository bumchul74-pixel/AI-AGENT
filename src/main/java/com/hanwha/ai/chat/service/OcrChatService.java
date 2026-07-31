package com.hanwha.ai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanwha.ai.chat.config.OcrChatProperties;
import com.hanwha.ai.chat.domain.ChatConversation;
import com.hanwha.ai.chat.domain.ChatMessage;
import com.hanwha.ai.chat.dto.ChatResponse;
import com.hanwha.ai.chat.repository.ChatRepository;
import com.hanwha.ai.global.exception.BusinessException;
import com.hanwha.ai.llm.dto.LlmGenerateRequest;
import com.hanwha.ai.llm.service.LlmClientFactory;
import com.hanwha.ai.mcp.gateway.EasyOcrMcpGatewayService;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OcrChatService {
    private static final String OCR_TOOL = "ocr_document_base64";

    private final ObjectProvider<EasyOcrMcpGatewayService> gatewayProvider;
    private final LlmClientFactory llmClientFactory;
    private final ChatRepository chatRepository;
    private final OcrChatProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OcrChatService(
            ObjectProvider<EasyOcrMcpGatewayService> gatewayProvider,
            LlmClientFactory llmClientFactory,
            ChatRepository chatRepository,
            OcrChatProperties properties
    ) {
        this.gatewayProvider = gatewayProvider;
        this.llmClientFactory = llmClientFactory;
        this.chatRepository = chatRepository;
        this.properties = properties;
    }

    public boolean supports(MultipartFile file) {
        String extension = extension(file == null ? null : file.getOriginalFilename());
        return properties.allowedExtensions().stream()
                .anyMatch(value -> value.equalsIgnoreCase(extension));
    }

    public ChatResponse extract(String message, Long conversationId, MultipartFile file) {
        String fileName = validate(file);
        byte[] content = bytes(file);
        McpSchema.CallToolResult result = gateway().callTool(OCR_TOOL, Map.of(
                "file_base64", Base64.getEncoder().encodeToString(content),
                "file_name", fileName,
                "max_pages", properties.maxPdfPages()));
        String extractedText = extractedText(result);
        String normalizedMessage = StringUtils.hasText(message)
                ? message.trim()
                : "첨부파일에서 추출된 텍스트를 정리해 주세요.";
        String answer = answer(normalizedMessage, fileName, extractedText);
        ChatConversation conversation = conversationId == null
                ? chatRepository.createConversation(createTitle(normalizedMessage))
                : requiredConversation(conversationId);
        saveMessages(conversation.getId(), normalizedMessage, fileName, content, answer);
        return new ChatResponse(answer, List.of(extractedText), conversation.getId(), true,
                "EasyOCR MCP · " + OCR_TOOL);
    }

    private String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("OCR로 처리할 PDF 또는 이미지 파일을 첨부해 주세요.");
        }
        if (file.getSize() > properties.maxFileSize().toBytes()) {
            throw new BusinessException("OCR 첨부파일은 "
                    + properties.maxFileSize().toMegabytes() + "MB 이하여야 합니다.");
        }
        String original = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String fileName = Path.of(original.replace('\\', '/')).getFileName().toString();
        if (!StringUtils.hasText(fileName) || original.contains("..") || !supports(file)) {
            throw new BusinessException("PDF 또는 지원되는 이미지 파일만 OCR 처리할 수 있습니다.");
        }
        return fileName;
    }

    private byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException("OCR 첨부파일을 읽지 못했습니다.", exception);
        }
    }

    private String extractedText(McpSchema.CallToolResult result) {
        if (result == null || Boolean.TRUE.equals(result.isError())) {
            throw new BusinessException("EasyOCR 문자 추출에 실패했습니다.");
        }
        String structured = findText(objectMapper.valueToTree(result.structuredContent()));
        if (StringUtils.hasText(structured)) {
            return structured.trim();
        }
        for (McpSchema.Content item : result.content() == null
                ? List.<McpSchema.Content>of() : result.content()) {
            if (item instanceof McpSchema.TextContent textContent) {
                String value = parseTextContent(textContent.text());
                if (StringUtils.hasText(value)) {
                    return value.trim();
                }
            }
        }
        throw new BusinessException("EasyOCR에서 추출된 문자가 없습니다.");
    }

    private String parseTextContent(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        try {
            String parsed = findText(objectMapper.readTree(value));
            return StringUtils.hasText(parsed) ? parsed : value;
        } catch (IOException exception) {
            return value;
        }
    }

    private String findText(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isObject()) {
            JsonNode text = node.get("text");
            if (text != null && text.isTextual() && StringUtils.hasText(text.asText())) {
                return text.asText();
            }
            for (String wrapper : List.of("result", "data", "content")) {
                String nested = findText(node.get(wrapper));
                if (StringUtils.hasText(nested)) {
                    return nested;
                }
            }
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String nested = findText(item);
                if (StringUtils.hasText(nested)) {
                    return nested;
                }
            }
        }
        return "";
    }

    private String answer(String message, String fileName, String extractedText) {
        String prompt = """
                당신은 OCR 문서 질의 도우미입니다.
                첨부파일에서 EasyOCR MCP로 추출한 문자만 근거로 사용자의 질문에 한국어로 답하세요.
                문자가 불완전하거나 질문의 근거가 없으면 그 사실을 명확히 알려 주세요.
                내용을 임의로 보완하거나 사실을 만들어내지 마세요.

                사용자 질문:
                %s

                첨부파일:
                %s

                OCR extracted text is supplied separately as context.
                """.formatted(message, fileName);
        return llmClientFactory.current()
                .generate(new LlmGenerateRequest(prompt, extractedText))
                .content();
    }

    private EasyOcrMcpGatewayService gateway() {
        return gatewayProvider.getIfAvailable(() -> {
            throw new BusinessException("EasyOCR MCP가 비활성화되어 있습니다.");
        });
    }

    private ChatConversation requiredConversation(Long conversationId) {
        ChatConversation conversation = chatRepository.findConversationById(conversationId);
        if (conversation == null) {
            throw new BusinessException("Conversation not found.");
        }
        return conversation;
    }

    private String createTitle(String message) {
        return message.length() <= 80 ? message : message.substring(0, 80) + "...";
    }

    private void saveMessages(Long conversationId, String message, String fileName,
            byte[] content, String answer) {
        chatRepository.save(new ChatMessage(
                null, conversationId, "user", message, fileName, content, LocalDateTime.now()));
        chatRepository.save(new ChatMessage(
                null, conversationId, "assistant", answer, null, true,
                "EasyOCR MCP · " + OCR_TOOL, LocalDateTime.now()));
    }

    private String extension(String fileName) {
        String value = fileName == null ? "" : fileName.trim();
        String extension = StringUtils.getFilenameExtension(value);
        return extension == null ? "" : extension;
    }
}
