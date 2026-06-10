package com.hanwha.ai.generation.domain;

import java.time.LocalDateTime;

public record GenerationHistory(
        Long id,
        String targetType,
        String prompt,
        String generatedCode,
        LocalDateTime createdAt
) {
}
