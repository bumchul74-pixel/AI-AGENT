package com.hanwha.ai.chat.dto;

import java.util.List;

public record ChatResponse(
        String message,
        List<String> ragDocuments,
        Long conversationId,
        boolean mcpContextApplied,
        String mcpReference
) {
    public ChatResponse(String message, List<String> ragDocuments, Long conversationId,
            boolean mcpContextApplied) {
        this(message, ragDocuments, conversationId, mcpContextApplied, null);
    }

    public ChatResponse(String message, List<String> ragDocuments, Long conversationId) {
        this(message, ragDocuments, conversationId, false, null);
    }

    public ChatResponse(String message, List<String> ragDocuments) {
        this(message, ragDocuments, null, false, null);
    }
}