package com.hanwha.ai.generation.dto;

import java.time.LocalDateTime;
import java.util.List;

public record GenerationHistoryResponse(
        Long id,
        String targetType,
        List<String> targetTypes,
        String prompt,
        String projectStructure,
        List<String> ragDocuments,
        String generatedCode,
        String llmProvider,
        String llmModel,
        LocalDateTime createdAt
) {
}
