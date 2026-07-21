package com.hanwha.ai.chat.dto;

import java.time.LocalDateTime;

public record ChatProjectResponse(
        Long id,
        String name,
        int conversationCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
