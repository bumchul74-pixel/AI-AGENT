package com.hanwha.ai.chat.dto;

import java.util.List;

public record ChatResponse(
        String message,
        List<String> ragDocuments,
        Long conversationId,
        boolean mcpContextApplied
) {
    public ChatResponse(String message, List<String> ragDocuments, Long conversationId) {
        this(message, ragDocuments, conversationId, false);
    }

    public ChatResponse(String message, List<String> ragDocuments) {
        this(message, ragDocuments, null, false);
    }
}