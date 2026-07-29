package com.hanwha.ai.chat.dto;

import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long id,
        Long conversationId,
        String role,
        String content,
        String attachmentName,
        boolean mcpContextApplied,
        LocalDateTime createdAt
) {
}