package com.hanwha.ai.chat.dto;

import com.hanwha.ai.mcp.orchestration.AgentExecutionView;
import java.util.List;

public record ChatResponse(
        String message,
        List<String> ragDocuments,
        Long conversationId,
        boolean mcpContextApplied,
        String mcpReference,
        AgentExecutionView agentExecution
) {
    public ChatResponse(String message, List<String> ragDocuments, Long conversationId,
            boolean mcpContextApplied, String mcpReference) {
        this(message, ragDocuments, conversationId, mcpContextApplied, mcpReference, null);
    }

    public ChatResponse(String message, List<String> ragDocuments, Long conversationId,
            boolean mcpContextApplied) {
        this(message, ragDocuments, conversationId, mcpContextApplied, null, null);
    }

    public ChatResponse(String message, List<String> ragDocuments, Long conversationId) {
        this(message, ragDocuments, conversationId, false, null, null);
    }

    public ChatResponse(String message, List<String> ragDocuments) {
        this(message, ragDocuments, null, false, null, null);
    }
}
