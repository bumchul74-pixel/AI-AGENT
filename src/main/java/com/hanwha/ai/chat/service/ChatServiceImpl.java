package com.hanwha.ai.chat.service;

import com.hanwha.ai.chat.domain.ChatConversation;
import com.hanwha.ai.chat.domain.ChatMessage;
import com.hanwha.ai.chat.domain.ChatProject;
import com.hanwha.ai.chat.dto.ChatConversationResponse;
import com.hanwha.ai.chat.dto.ChatMessageResponse;
import com.hanwha.ai.chat.dto.ChatRequest;
import com.hanwha.ai.chat.dto.ChatResponse;
import com.hanwha.ai.chat.dto.ChatProjectResponse;
import com.hanwha.ai.chat.repository.ChatRepository;
import com.hanwha.ai.global.exception.BusinessException;
import com.hanwha.ai.llm.dto.LlmGenerateRequest;
import com.hanwha.ai.llm.service.LlmClientFactory;
import com.hanwha.ai.mcp.service.McpChatContextProvider;
import com.hanwha.ai.rag.dto.RagSearchRequest;
import com.hanwha.ai.rag.dto.RagSearchResponse;
import com.hanwha.ai.rag.dto.HybridSearchResult;
import com.hanwha.ai.rag.service.HybridSearchService;
import com.hanwha.ai.rag.service.RagClient;
import com.hanwha.ai.sourcegraph.service.NoOpSourceGraphService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class ChatServiceImpl implements ChatService {
    private final HybridSearchService hybridSearchService;
    private final LlmClientFactory llmClientFactory;
    private final ChatRepository chatRepository;
    private final McpChatContextProvider mcpChatContextProvider;

    public ChatServiceImpl(
            RagClient ragClient,
            LlmClientFactory llmClientFactory,
            ChatRepository chatRepository,
            McpChatContextProvider mcpChatContextProvider
    ) {
        this(ragClient, llmClientFactory, chatRepository, mcpChatContextProvider,
                new HybridSearchService(ragClient, NoOpSourceGraphService.INSTANCE));
    }

    @Autowired
    public ChatServiceImpl(
            RagClient ragClient,
            LlmClientFactory llmClientFactory,
            ChatRepository chatRepository,
            McpChatContextProvider mcpChatContextProvider,
            HybridSearchService hybridSearchService
    ) {
        this.hybridSearchService = hybridSearchService;
        this.llmClientFactory = llmClientFactory;
        this.chatRepository = chatRepository;
        this.mcpChatContextProvider = mcpChatContextProvider;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        ChatConversation conversation = resolveConversation(request.conversationId(), request.message());
        List<ChatMessage> history = chatRepository.findRecentMessages(conversation.getId(), 20);
        if (mcpChatContextProvider.supports(request.message())) {
            return chatWithMcp(request, conversation, history);
        }

        HybridSearchResult hybridResult = hybridSearchService.search(new RagSearchRequest(request.message()));
        RagSearchResponse ragSearchResponse = new RagSearchResponse(hybridResult.documents());
        String prompt = buildRagPrompt(request, ragSearchResponse, formatHistory(history));
        List<String> documents = hybridResult.documents();
        String answer = generateAnswer(prompt, hybridResult.context());

        saveMessages(conversation.getId(), request.message(), answer);

        return new ChatResponse(answer, documents, conversation.getId());
    }

    private ChatResponse chatWithMcp(
            ChatRequest request,
            ChatConversation conversation,
            List<ChatMessage> history
    ) {
        List<String> mcpContexts = mcpChatContextProvider.resolveContext(request.message());
        String context = String.join("\n\n--- MCP RESULT ---\n\n", mcpContexts);
        String prompt = buildMcpPrompt(request, context, formatHistory(history));
        String answer = generateAnswer(prompt, context);

        saveMessages(conversation.getId(), request.message(), answer);

        return new ChatResponse(answer, mcpContexts, conversation.getId());
    }

    private String generateAnswer(String prompt, String context) {
        return llmClientFactory.current()
                .generate(new LlmGenerateRequest(prompt, context))
                .content();
    }

    private void saveMessages(Long conversationId, String userMessage, String answer) {
        chatRepository.save(new ChatMessage(
                null, conversationId, "user", userMessage, null, LocalDateTime.now()));
        chatRepository.save(new ChatMessage(
                null, conversationId, "assistant", answer, null, LocalDateTime.now()));
    }

    @Override
    public List<ChatConversationResponse> findConversations() {
        return chatRepository.findConversations().stream()
                .map(this::toConversationResponse)
                .toList();
    }

    @Override
    public List<ChatProjectResponse> findProjects() {
        return chatRepository.findProjects().stream()
                .map(this::toProjectResponse)
                .toList();
    }

    @Override
    public ChatProjectResponse createProject(String name) {
        return toProjectResponse(chatRepository.createProject(normalizeProjectName(name)));
    }

    @Override
    public ChatProjectResponse renameProject(Long projectId, String name) {
        requiredProject(projectId);
        String normalizedName = normalizeProjectName(name);
        if (!chatRepository.updateProjectName(projectId, normalizedName)) {
            throw new BusinessException("Project name could not be changed.");
        }
        return toProjectResponse(requiredProject(projectId));
    }

    @Override
    public void moveConversation(Long conversationId, Long projectId) {
        requiredConversation(conversationId);
        if (projectId != null) {
            requiredProject(projectId);
        }
        if (!chatRepository.updateConversationProject(conversationId, projectId)) {
            throw new BusinessException("Conversation could not be moved.");
        }
    }

    @Override
    public List<ChatMessageResponse> findMessages(Long conversationId) {
        requiredConversation(conversationId);
        return chatRepository.findMessages(conversationId).stream()
                .map(message -> new ChatMessageResponse(
                        message.getId(), message.getConversationId(), message.getRole(),
                        message.getMessage(), message.getAttachmentName(), message.getCreatedAt()
                ))
                .toList();
    }

    @Override
    public ChatMessage findAttachment(Long messageId) {
        ChatMessage message = chatRepository.findMessageById(messageId);
        if (message == null || !"user".equals(message.getRole())
                || message.getAttachmentName() == null
                || message.getAttachmentContent() == null
                || message.getAttachmentContent().length == 0) {
            throw new BusinessException("Message attachment not found.");
        }
        return message;
    }

    @Override
    public void deleteConversation(Long conversationId) {
        requiredConversation(conversationId);
        if (!chatRepository.deleteConversation(conversationId)) {
            throw new BusinessException("Conversation could not be deleted.");
        }
    }

    private ChatConversation resolveConversation(Long conversationId, String firstMessage) {
        if (conversationId == null) {
            return chatRepository.createConversation(createTitle(firstMessage));
        }
        return requiredConversation(conversationId);
    }

    private ChatConversation requiredConversation(Long conversationId) {
        ChatConversation conversation = chatRepository.findConversationById(conversationId);
        if (conversation == null) {
            throw new BusinessException("Conversation not found.");
        }
        return conversation;
    }

    private ChatProject requiredProject(Long projectId) {
        ChatProject project = chatRepository.findProjectById(projectId);
        if (project == null) {
            throw new BusinessException("Project not found.");
        }
        return project;
    }

    private String normalizeProjectName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty()) {
            throw new BusinessException("Project name is required.");
        }
        if (normalized.length() > 80) {
            throw new BusinessException("Project name must be 80 characters or fewer.");
        }
        return normalized;
    }

    private String createTitle(String message) {
        String normalized = message == null ? "" : message.trim();
        if (normalized.isBlank()) {
            return "New conversation";
        }
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80) + "...";
    }

    private String formatHistory(List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return "(no previous messages)";
        }
        return messages.stream()
                .map(message -> message.getRole() + ": " + message.getMessage())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("(no previous messages)");
    }

    private ChatConversationResponse toConversationResponse(ChatConversation conversation) {
        return new ChatConversationResponse(
                conversation.getId(), conversation.getTitle(), conversation.getProjectId(),
                conversation.getLastMessage(),
                conversation.getMessageCount(), conversation.getCreatedAt(), conversation.getUpdatedAt()
        );
    }

    private ChatProjectResponse toProjectResponse(ChatProject project) {
        return new ChatProjectResponse(
                project.getId(), project.getName(), project.getConversationCount(),
                project.getCreatedAt(), project.getUpdatedAt()
        );
    }

    private String buildRagPrompt(
            ChatRequest request,
            RagSearchResponse ragSearchResponse,
            String history
    ) {
        return """
                You are an assistant for a Spring Boot code generation system.
                Answer using the retrieved RAG documents and project source patterns.

                User message:
                %s

                Previous conversation:
                %s

                Retrieved documents:
                %s
                """.formatted(
                        request.message(),
                        history,
                        String.join("\n", ragSearchResponse.documents())
                );
    }

    private String buildMcpPrompt(ChatRequest request, String mcpContext, String history) {
        return """
                You are an assistant for a Spring Boot code generation system.
                The user is asking about an MCP server connected to this application.
                Answer in a friendly, concise way using only the MCP gateway result below.
                If a requested MCP detail is not present in the MCP result, say that it was not returned.

                User message:
                %s

                Previous conversation:
                %s

                MCP gateway result:
                %s
                """.formatted(request.message(), history, mcpContext);
    }
}
