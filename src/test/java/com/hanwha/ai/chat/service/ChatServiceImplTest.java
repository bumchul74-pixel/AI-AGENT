package com.hanwha.ai.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanwha.ai.chat.dto.ChatRequest;
import com.hanwha.ai.chat.repository.ChatRepository;
import com.hanwha.ai.llm.config.LlmProperties;
import com.hanwha.ai.llm.domain.LlmProvider;
import com.hanwha.ai.llm.dto.LlmGenerateRequest;
import com.hanwha.ai.llm.dto.LlmGenerateResponse;
import com.hanwha.ai.llm.service.LlmClient;
import com.hanwha.ai.llm.service.LlmClientFactory;
import com.hanwha.ai.mcp.service.McpChatContextProvider;
import com.hanwha.ai.rag.dto.RagSearchResponse;
import com.hanwha.ai.rag.service.RagClient;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ChatServiceImplTest {
    @Test
    void chatUsesRagDocumentsBeforeLlmResponse() {
        RagClient ragClient = request -> new RagSearchResponse(List.of("standard controller source pattern"));
        LlmClientFactory llmClientFactory = new LlmClientFactory(
                new LlmProperties("openai"),
                List.of(fakeLlmClient(new AtomicReference<>()))
        );
        ChatService chatService = new ChatServiceImpl(
                ragClient,
                llmClientFactory,
                new ChatRepository(),
                noOpMcpContextProvider()
        );

        var response = chatService.chat(new ChatRequest("How should I create a user controller?"));

        assertThat(response.message()).contains("standard controller source pattern");
        assertThat(response.ragDocuments()).containsExactly("standard controller source pattern");
    }

    @Test
    void chatUsesMcpContextWhenQuestionTargetsMcp() {
        AtomicBoolean ragCalled = new AtomicBoolean(false);
        RagClient ragClient = request -> {
            ragCalled.set(true);
            return new RagSearchResponse(List.of("rag should not be used"));
        };
        AtomicReference<LlmGenerateRequest> llmRequest = new AtomicReference<>();
        LlmClientFactory llmClientFactory = new LlmClientFactory(
                new LlmProperties("openai"),
                List.of(fakeLlmClient(llmRequest))
        );
        McpChatContextProvider mcpContextProvider = new McpChatContextProvider() {
            @Override
            public boolean supports(String message) {
                return message.toLowerCase().contains("mcp");
            }

            @Override
            public List<String> resolveContext(String message) {
                return List.of("MCP gateway operation: tools/list\nMCP gateway result: get_server_info");
            }
        };
        ChatService chatService = new ChatServiceImpl(
                ragClient,
                llmClientFactory,
                new ChatRepository(),
                mcpContextProvider
        );

        var response = chatService.chat(new ChatRequest("MCP tool 목록 알려줘"));

        assertThat(ragCalled.get()).isFalse();
        assertThat(response.message()).contains("get_server_info");
        assertThat(response.ragDocuments()).containsExactly("MCP gateway operation: tools/list\nMCP gateway result: get_server_info");
        assertThat(llmRequest.get().prompt()).contains("MCP gateway result", "MCP tool 목록 알려줘");
        assertThat(llmRequest.get().context()).contains("tools/list", "get_server_info");
    }

    private McpChatContextProvider noOpMcpContextProvider() {
        return new McpChatContextProvider() {
            @Override
            public boolean supports(String message) {
                return false;
            }

            @Override
            public List<String> resolveContext(String message) {
                return List.of();
            }
        };
    }

    private LlmClient fakeLlmClient(AtomicReference<LlmGenerateRequest> llmRequest) {
        return new LlmClient() {
            @Override
            public LlmProvider provider() {
                return LlmProvider.OPENAI;
            }

            @Override
            public LlmGenerateResponse generate(LlmGenerateRequest request) {
                llmRequest.set(request);
                return new LlmGenerateResponse(request.context());
            }
        };
    }
}