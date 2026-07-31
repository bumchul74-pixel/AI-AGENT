package com.hanwha.ai.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hanwha.ai.chat.config.OcrChatProperties;
import com.hanwha.ai.chat.repository.ChatRepository;
import com.hanwha.ai.global.exception.BusinessException;
import com.hanwha.ai.llm.config.LlmProperties;
import com.hanwha.ai.llm.domain.LlmProvider;
import com.hanwha.ai.llm.dto.LlmGenerateRequest;
import com.hanwha.ai.llm.dto.LlmGenerateResponse;
import com.hanwha.ai.llm.service.LlmClient;
import com.hanwha.ai.llm.service.LlmClientFactory;
import com.hanwha.ai.mcp.gateway.EasyOcrMcpGatewayService;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

class OcrChatServiceTest {
    @Test
    void sendsPdfToEasyOcrMcpAndUsesExtractedTextAsLlmContext() {
        AtomicReference<String> toolName = new AtomicReference<>();
        AtomicReference<Map<String, Object>> arguments = new AtomicReference<>();
        EasyOcrMcpGatewayService gateway = new EasyOcrMcpGatewayService(null) {
            @Override
            public McpSchema.CallToolResult callTool(String name, Map<String, Object> toolArguments) {
                toolName.set(name);
                arguments.set(toolArguments);
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("{\"text\":\"계약 금액은 1억원입니다.\"}")),
                        false, null, Map.of());
            }
        };
        OcrChatService service = service(gateway);
        byte[] pdf = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "contract.pdf", "application/pdf", pdf);

        var response = service.extract("계약 금액이 얼마야?", null, file);

        assertThat(response.message()).isEqualTo("계약 금액은 1억원입니다.");
        assertThat(response.mcpContextApplied()).isTrue();
        assertThat(response.mcpReference()).isEqualTo("EasyOCR MCP · ocr_document_base64");
        assertThat(toolName.get()).isEqualTo("ocr_document_base64");
        assertThat(arguments.get())
                .containsEntry("file_name", "contract.pdf")
                .containsEntry("max_pages", 10)
                .containsEntry("file_base64", Base64.getEncoder().encodeToString(pdf));
    }

    @Test
    void rejectsUnsupportedAttachment() {
        OcrChatService service = service(null);
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "text".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.extract("읽어줘", null, file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PDF");
    }

    private OcrChatService service(EasyOcrMcpGatewayService gateway) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        if (gateway != null) {
            beanFactory.registerSingleton("easyOcrMcpGatewayService", gateway);
        }
        LlmClient client = new LlmClient() {
            @Override public LlmProvider provider() { return LlmProvider.OPENAI; }
            @Override public LlmGenerateResponse generate(LlmGenerateRequest request) {
                return new LlmGenerateResponse(request.context());
            }
        };
        return new OcrChatService(
                beanFactory.getBeanProvider(EasyOcrMcpGatewayService.class),
                new LlmClientFactory(new LlmProperties("openai"), List.of(client)),
                new ChatRepository(),
                new OcrChatProperties(DataSize.ofMegabytes(20), 10, List.of("pdf", "png")));
    }
}
