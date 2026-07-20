package com.hanwha.ai.chat.dto;

public record ChatRequest(String message, Long conversationId) {
    public ChatRequest(String message) {
        this(message, null);
    }
}
