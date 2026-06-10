package com.hanwha.ai.generation.dto;

import java.util.List;

public record GenerationResponse(
        String targetType,
        String generatedCode,
        List<String> ragDocuments
) {
}
