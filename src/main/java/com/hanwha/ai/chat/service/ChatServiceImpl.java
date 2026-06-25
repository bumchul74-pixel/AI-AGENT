package com.hanwha.ai.chat.service;

import com.hanwha.ai.chat.domain.ChatMessage;
import com.hanwha.ai.chat.dto.ChatRequest;
import com.hanwha.ai.chat.dto.ChatResponse;
import com.hanwha.ai.chat.repository.ChatRepository;
import com.hanwha.ai.llm.dto.LlmGenerateRequest;
import com.hanwha.ai.llm.service.LlmClientFactory;
import com.hanwha.ai.mcp.service.McpChatContextProvider;
import com.hanwha.ai.rag.dto.RagSearchRequest;
import com.hanwha.ai.rag.dto.RagSearchResponse;
import com.hanwha.ai.rag.service.RagClient;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ChatServiceImpl implements ChatService {
    private final RagClient ragClient;
    private final LlmClientFactory llmClientFactory;
    private final ChatRepository chatRepository;
    private final McpChatContextProvider mcpChatContextProvider;

    public ChatServiceImpl(
            RagClient ragClient,
            LlmClientFactory llmClientFactory,
            ChatRepository chatRepository,
            McpChatContextProvider mcpChatContextProvider
    ) {
        this.ragClient = ragClient;
        this.llmClientFactory = llmClientFactory;
        this.chatRepository = chatRepository;
        this.mcpChatContextProvider = mcpChatContextProvider;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        if (mcpChatContextProvider.supports(request.message())) {
            return chatWithMcp(request);
        }

        RagSearchResponse ragSearchResponse = ragClient.search(new RagSearchRequest(request.message()));
        String prompt = buildRagPrompt(request, ragSearchResponse);
        List<String> documents = ragSearchResponse.documents();
        String answer = generateAnswer(prompt, String.join("\n", documents));

        saveMessages(request.message(), answer);

        return new ChatResponse(answer, documents);
    }

    private ChatResponse chatWithMcp(ChatRequest request) {
        List<String> mcpContexts = mcpChatContextProvider.resolveContext(request.message());
        String context = String.join("\n\n--- MCP RESULT ---\n\n", mcpContexts);
        String prompt = buildMcpPrompt(request, context);
        String answer = generateAnswer(prompt, context);

        saveMessages(request.message(), answer);

        return new ChatResponse(answer, mcpContexts);
    }

    private String generateAnswer(String prompt, String context) {
        return llmClientFactory.current()
                .generate(new LlmGenerateRequest(prompt, context))
                .content();
    }

    private void saveMessages(String userMessage, String answer) {
        chatRepository.save(new ChatMessage(null, "user", userMessage, LocalDateTime.now()));
        chatRepository.save(new ChatMessage(null, "assistant", answer, LocalDateTime.now()));
    }

    private String buildRagPrompt(ChatRequest request, RagSearchResponse ragSearchResponse) {
        return """
                You are an assistant for a Spring Boot code generation system.
                Answer using the retrieved RAG documents and project source patterns.

                User message:
                %s

                Retrieved documents:
                %s
                """.formatted(request.message(), String.join("\n", ragSearchResponse.documents()));
    }

    private String buildMcpPrompt(ChatRequest request, String mcpContext) {
        return """
                You are an assistant for a Spring Boot code generation system.
                The user is asking about an MCP server connected to this application.
                Answer in a friendly, concise way using only the MCP gateway result below.
                If a requested MCP detail is not present in the MCP result, say that it was not returned.

                User message:
                %s

                MCP gateway result:
                %s
                """.formatted(request.message(), mcpContext);
    }
}