package com.hanwha.ai.generation.dto;

public record GenerationRequest(
        String targetType,
        String prompt
) {
}
