package com.hanwha.ai.chat.dto;

import java.time.LocalDateTime;

public record ChatConversationResponse(
        Long id,
        String title,
        Long projectId,
        String lastMessage,
        int messageCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
