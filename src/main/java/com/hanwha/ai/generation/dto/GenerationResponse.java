package com.hanwha.ai.generation.dto;

import java.util.List;

public record GenerationResponse(
        String targetType,
        List<String> targetTypes,
        String generatedCode,
        List<String> ragDocuments,
        String projectStructure,
        Long historyId,
        boolean mcpContextApplied,
        String mcpContextMessage
) {
    public GenerationResponse(String targetType, List<String> targetTypes, String generatedCode, List<String> ragDocuments, String projectStructure) {
        this(targetType, targetTypes, generatedCode, ragDocuments, projectStructure, null);
    }

    public GenerationResponse(
            String targetType,
            List<String> targetTypes,
            String generatedCode,
            List<String> ragDocuments,
            String projectStructure,
            Long historyId
    ) {
        this(targetType, targetTypes, generatedCode, ragDocuments, projectStructure, historyId, false, "");
    }

    public GenerationResponse(String targetType, String generatedCode, List<String> ragDocuments) {
        this(targetType, List.of(targetType), generatedCode, ragDocuments, "", null);
    }

    public GenerationResponse(String targetType, String generatedCode, List<String> ragDocuments, String projectStructure) {
        this(targetType, List.of(targetType), generatedCode, ragDocuments, projectStructure, null);
    }
}
