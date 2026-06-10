package com.hanwha.ai.chat.domain;

import java.time.LocalDateTime;

public record ChatMessage(
        Long id,
        String role,
        String message,
        LocalDateTime createdAt
) {
}
