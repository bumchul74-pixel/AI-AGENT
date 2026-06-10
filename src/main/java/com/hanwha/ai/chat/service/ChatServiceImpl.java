package com.hanwha.ai.chat.service;

import com.hanwha.ai.chat.domain.ChatMessage;
import com.hanwha.ai.chat.dto.ChatRequest;
import com.hanwha.ai.chat.dto.ChatResponse;
import com.hanwha.ai.chat.repository.ChatRepository;
import com.hanwha.ai.llm.dto.LlmGenerateRequest;
import com.hanwha.ai.llm.service.LlmClientFactory;
import com.hanwha.ai.rag.dto.RagSearchRequest;
import com.hanwha.ai.rag.dto.RagSearchResponse;
import com.hanwha.ai.rag.service.RagClient;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class ChatServiceImpl implements ChatService {
    private final RagClient ragClient;
    private final LlmClientFactory llmClientFactory;
    private final ChatRepository chatRepository;

    public ChatServiceImpl(
            RagClient ragClient,
            LlmClientFactory llmClientFactory,
            ChatRepository chatRepository
    ) {
        this.ragClient = ragClient;
        this.llmClientFactory = llmClientFactory;
        this.chatRepository = chatRepository;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        RagSearchResponse ragSearchResponse = ragClient.search(new RagSearchRequest(request.message()));
        String prompt = buildPrompt(request, ragSearchResponse);
        String answer = llmClientFactory.current()
                .generate(new LlmGenerateRequest(prompt, String.join("\n", ragSearchResponse.documents())))
                .content();

        chatRepository.save(new ChatMessage(null, "user", request.message(), LocalDateTime.now()));
        chatRepository.save(new ChatMessage(null, "assistant", answer, LocalDateTime.now()));

        return new ChatResponse(answer, ragSearchResponse.documents());
    }

    private String buildPrompt(ChatRequest request, RagSearchResponse ragSearchResponse) {
        return """
                You are an assistant for a Spring Boot code generation system.
                Answer using the retrieved RAG documents and project source patterns.

                User message:
                %s

                Retrieved documents:
                %s
                """.formatted(request.message(), String.join("\n", ragSearchResponse.documents()));
    }
}
