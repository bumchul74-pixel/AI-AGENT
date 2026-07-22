package com.hanwha.ai.generation.dto;

import java.time.LocalDateTime;
import java.util.List;

public record GenerationHistoryResponse(
        Long id,
        String targetType,
        List<String> targetTypes,
        String prompt,
        String projectKey,
        String projectStructure,
        List<String> ragDocuments,
        String generatedCode,
        String llmProvider,
        String llmModel,
        String neo4jIndexStatus,
        LocalDateTime neo4jIndexedAt,
        String neo4jIndexError,
        LocalDateTime createdAt
) {
}
