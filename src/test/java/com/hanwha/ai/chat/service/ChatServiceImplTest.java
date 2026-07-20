package com.hanwha.ai.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanwha.ai.chat.domain.ChatConversation;
import com.hanwha.ai.chat.domain.ChatMessage;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ChatServiceImplTest {
    @Test
    void chatIncludesRecentConversationHistoryAndKeepsConversationId() {
        AtomicReference<LlmGenerateRequest> llmRequest = new AtomicReference<>();
        ChatConversation conversation = new ChatConversation();
        conversation.setId(7L);
        conversation.setTitle("User controller");
        List<ChatMessage> savedMessages = new ArrayList<>();
        ChatRepository repository = new ChatRepository() {
            @Override
            public ChatConversation findConversationById(Long id) {
                return conversation;
            }

            @Override
            public List<ChatMessage> findRecentMessages(Long conversationId, int limit) {
                return List.of(
                        new ChatMessage(1L, 7L, "user", "이전 질문", null, LocalDateTime.now()),
                        new ChatMessage(2L, 7L, "assistant", "이전 답변", null, LocalDateTime.now())
                );
            }

            @Override
            public void save(ChatMessage message) {
                savedMessages.add(message);
            }
        };
        ChatService service = new ChatServiceImpl(
                request -> new RagSearchResponse(List.of("standard pattern")),
                new LlmClientFactory(
                        new LlmProperties("openai"),
                        List.of(fakeLlmClient(llmRequest))
                ),
                repository,
                noOpMcpContextProvider()
        );

        var response = service.chat(new ChatRequest("후속 질문", 7L));

        assertThat(response.conversationId()).isEqualTo(7L);
        assertThat(llmRequest.get().prompt()).contains("이전 질문", "이전 답변", "후속 질문");
        assertThat(savedMessages).extracting(ChatMessage::getConversationId)
                .containsExactly(7L, 7L);
    }

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
