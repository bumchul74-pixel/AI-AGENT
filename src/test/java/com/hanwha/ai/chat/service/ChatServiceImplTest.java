package com.hanwha.ai.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanwha.ai.chat.dto.ChatRequest;
import com.hanwha.ai.chat.repository.ChatRepository;
import com.hanwha.ai.llm.config.LlmProperties;
import com.hanwha.ai.llm.domain.LlmProvider;
import com.hanwha.ai.llm.dto.LlmGenerateResponse;
import com.hanwha.ai.llm.service.LlmClient;
import com.hanwha.ai.llm.service.LlmClientFactory;
import com.hanwha.ai.rag.dto.RagSearchResponse;
import com.hanwha.ai.rag.service.RagClient;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatServiceImplTest {
    @Test
    void chatUsesRagDocumentsBeforeLlmResponse() {
        RagClient ragClient = request -> new RagSearchResponse(List.of("standard controller source pattern"));
        LlmClientFactory llmClientFactory = new LlmClientFactory(
                new LlmProperties("openai"),
                List.of(fakeLlmClient())
        );
        ChatService chatService = new ChatServiceImpl(ragClient, llmClientFactory, new ChatRepository());

        var response = chatService.chat(new ChatRequest("How should I create a user controller?"));

        assertThat(response.message()).contains("standard controller source pattern");
        assertThat(response.ragDocuments()).containsExactly("standard controller source pattern");
    }

    private LlmClient fakeLlmClient() {
        return new LlmClient() {
            @Override
            public LlmProvider provider() {
                return LlmProvider.OPENAI;
            }

            @Override
            public LlmGenerateResponse generate(com.hanwha.ai.llm.dto.LlmGenerateRequest request) {
                return new LlmGenerateResponse(request.context());
            }
        };
    }
}
