package com.hanwha.ai.chat.service;

import com.hanwha.ai.chat.config.SecureCodingProperties;
import com.hanwha.ai.chat.domain.ChatConversation;
import com.hanwha.ai.chat.domain.ChatMessage;
import com.hanwha.ai.chat.dto.ChatResponse;
import com.hanwha.ai.chat.repository.ChatRepository;
import com.hanwha.ai.global.exception.BusinessException;
import com.hanwha.ai.llm.dto.LlmGenerateRequest;
import com.hanwha.ai.llm.service.LlmClientFactory;
import com.hanwha.ai.mcp.gateway.AiMcpGatewayService;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SecureCodingChatService {
    private final ObjectProvider<AiMcpGatewayService> gatewayProvider;
    private final LlmClientFactory llmClientFactory;
    private final ChatRepository chatRepository;
    private final SecureCodingProperties properties;

    public SecureCodingChatService(ObjectProvider<AiMcpGatewayService> gatewayProvider,
            LlmClientFactory llmClientFactory, ChatRepository chatRepository,
            SecureCodingProperties properties) {
        this.gatewayProvider = gatewayProvider;
        this.llmClientFactory = llmClientFactory;
        this.chatRepository = chatRepository;
        this.properties = properties;
    }

    public ChatResponse scan(String message, MultipartFile file) {
        return scan(message, null, file);
    }

    public ChatResponse scan(String message, Long conversationId, MultipartFile file) {
        ChatConversation conversation = conversationId == null
                ? null
                : requiredConversation(conversationId);
        String fileName = validateFile(file);
        String source = decodeUtf8(file);
        McpSchema.CallToolResult result = gateway().callTool("scan_source", Map.of(
                "fileName", fileName,
                "source", source,
                "ruleSets", properties.ruleSets().stream().filter(StringUtils::hasText).toList()));
        String scanResult = successfulResult(result);
        String answer = summarize(message, fileName, scanResult);
        if (conversation == null) {
            conversation = chatRepository.createConversation(createTitle(message));
        }
        saveMessages(conversation.getId(), message, fileName, source, answer);
        return new ChatResponse(answer, List.of(scanResult), conversation.getId());
    }

    private String validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Attach a Java source file to scan.");
        }
        if (file.getSize() > properties.maxFileSize().toBytes()) {
            throw new BusinessException("The attachment exceeds the secure coding file size limit.");
        }
        String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String fileName = Path.of(original.replace('\\', '/')).getFileName().toString();
        String extension = StringUtils.getFilenameExtension(fileName);
        boolean allowed = extension != null && properties.allowedExtensions().stream()
                .anyMatch(value -> value.equalsIgnoreCase(extension));
        if (!StringUtils.hasText(fileName) || original.contains("..") || !allowed) {
            throw new BusinessException("Only a valid Java source file can be scanned.");
        }
        return fileName;
    }

    private String decodeUtf8(MultipartFile file) {
        try {
            var decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            String source = decoder.decode(ByteBuffer.wrap(file.getBytes())).toString();
            if (!StringUtils.hasText(source)) {
                throw new BusinessException("The Java source attachment is empty.");
            }
            return source;
        } catch (IOException exception) {
            throw new BusinessException("The attachment must be valid UTF-8 Java source.", exception);
        }
    }

    private AiMcpGatewayService gateway() {
        return gatewayProvider.getIfAvailable(() -> {
            throw new BusinessException("AI-MCP is disabled. Check MCP_CLIENT_ENABLED.");
        });
    }

    private String successfulResult(McpSchema.CallToolResult result) {
        String text = formatResult(result);
        if (result == null || Boolean.TRUE.equals(result.isError())) {
            throw new BusinessException("Semgrep CE scan failed: " + text);
        }
        if (!StringUtils.hasText(text)) {
            throw new BusinessException("Semgrep CE returned an empty scan result.");
        }
        return text;
    }

    private String formatResult(McpSchema.CallToolResult result) {
        if (result == null) {
            return "No response";
        }
        String text = (result.content() == null ? List.<McpSchema.Content>of() : result.content()).stream()
                .map(item -> item instanceof McpSchema.TextContent content ? content.text() : String.valueOf(item))
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n"));
        if (result.structuredContent() == null) {
            return text;
        }
        return text.isBlank() ? String.valueOf(result.structuredContent())
                : text + "\n" + result.structuredContent();
    }

    private String summarize(String message, String fileName, String scanResult) {
        String prompt = """
                You are a secure coding reviewer. Summarize this Semgrep CE result in Korean.
                Include file, severity, rule, line, risk, and remediation. Do not invent findings.
                If there are no findings, clearly say so.

                User command: %s
                Attached file: %s
                Semgrep CE MCP result:
                %s
                """.formatted(message, fileName, scanResult);
        return llmClientFactory.current().generate(new LlmGenerateRequest(prompt, scanResult)).content();
    }

    private ChatConversation requiredConversation(Long conversationId) {
        ChatConversation conversation = chatRepository.findConversationById(conversationId);
        if (conversation == null) {
            throw new BusinessException("Conversation not found.");
        }
        return conversation;
    }

    private String createTitle(String message) {
        String normalized = message == null ? "" : message.trim();
        if (normalized.isBlank()) {
            return "Secure coding review";
        }
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80) + "...";
    }

    private void saveMessages(Long conversationId, String message, String fileName,
            String source, String answer) {
        chatRepository.save(new ChatMessage(
                null, conversationId, "user", message, fileName,
                source.getBytes(StandardCharsets.UTF_8), LocalDateTime.now()));
        chatRepository.save(new ChatMessage(
                null, conversationId, "assistant", answer, null, LocalDateTime.now()));
    }
}
