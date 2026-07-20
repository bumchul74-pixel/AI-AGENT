package com.hanwha.ai.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hanwha.ai.chat.config.SecureCodingProperties;
import com.hanwha.ai.chat.repository.ChatRepository;
import com.hanwha.ai.global.exception.BusinessException;
import com.hanwha.ai.llm.config.LlmProperties;
import com.hanwha.ai.llm.domain.LlmProvider;
import com.hanwha.ai.llm.dto.LlmGenerateRequest;
import com.hanwha.ai.llm.dto.LlmGenerateResponse;
import com.hanwha.ai.llm.service.LlmClient;
import com.hanwha.ai.llm.service.LlmClientFactory;
import com.hanwha.ai.mcp.gateway.AiMcpGatewayService;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

class SecureCodingChatServiceTest {
    @Test
    void convertsJavaFileToUtf8AndCallsAiMcpScanSource() {
        AtomicReference<String> toolName = new AtomicReference<>();
        AtomicReference<Map<String, Object>> arguments = new AtomicReference<>();
        AiMcpGatewayService gateway = new AiMcpGatewayService(null) {
            @Override
            public McpSchema.CallToolResult callTool(String name, Map<String, Object> toolArguments) {
                toolName.set(name);
                arguments.set(toolArguments);
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("HIGH: SQL injection at line 12")),
                        false, null, Map.of());
            }
        };
        SecureCodingChatService service = service(gateway);
        String source = "class 한글서비스 {}";
        MockMultipartFile file = new MockMultipartFile(
                "file", "UnsafeMapper.java", "text/x-java-source", source.getBytes(StandardCharsets.UTF_8));

        var response = service.scan("Check the attached Java source for vulnerabilities.", file);

        assertThat(response.message()).contains("HIGH: SQL injection at line 12");
        assertThat(toolName.get()).isEqualTo("scan_source");
        assertThat(arguments.get())
                .containsEntry("fileName", "UnsafeMapper.java")
                .containsEntry("source", source)
                .containsEntry("ruleSets", List.of("java-security"));
    }

    @Test
    void rejectsMalformedUtf8BeforeCallingMcp() {
        SecureCodingChatService service = service(null);
        MockMultipartFile file = new MockMultipartFile(
                "file", "Broken.java", "text/x-java-source", new byte[]{(byte) 0xC3, 0x28});

        assertThatThrownBy(() -> service.scan("MCP SecureCoding scan", file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("UTF-8");
    }

    @Test
    void rejectsNonJavaAttachmentBeforeCallingMcp() {
        SecureCodingChatService service = service(null);
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "text".getBytes());

        assertThatThrownBy(() -> service.scan("MCP SecureCoding scan", file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Java");
    }

    private SecureCodingChatService service(AiMcpGatewayService gateway) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        if (gateway != null) {
            beanFactory.registerSingleton("aiMcpGatewayService", gateway);
        }
        LlmClient llmClient = new LlmClient() {
            @Override public LlmProvider provider() { return LlmProvider.OPENAI; }
            @Override public LlmGenerateResponse generate(LlmGenerateRequest request) {
                return new LlmGenerateResponse(request.context());
            }
        };
        LlmClientFactory llmFactory = new LlmClientFactory(
                new LlmProperties("openai"), List.of(llmClient));
        SecureCodingProperties properties = new SecureCodingProperties(
                DataSize.ofMegabytes(1), List.of("java"), List.of("java-security"));
        return new SecureCodingChatService(
                beanFactory.getBeanProvider(AiMcpGatewayService.class),
                llmFactory, new ChatRepository(), properties);
    }
}