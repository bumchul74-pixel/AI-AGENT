package com.hanwha.ai.chat.dto;

import java.util.List;

public record ChatResponse(
        String message,
        List<String> ragDocuments,
        Long conversationId
) {
    public ChatResponse(String message, List<String> ragDocuments) {
        this(message, ragDocuments, null);
    }
}
