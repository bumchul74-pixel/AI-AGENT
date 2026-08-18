package com.hanwha.ai.sourcequality.dto;

public record SourceQualitySummaryResponse(
        int totalMethodCount,
        int duplicateMethodCount,
        int duplicateGroupCount,
        double duplicateRatio,
        int highComplexityCount,
        int maxCyclomaticComplexity,
        int maxCognitiveComplexity
) {
}
