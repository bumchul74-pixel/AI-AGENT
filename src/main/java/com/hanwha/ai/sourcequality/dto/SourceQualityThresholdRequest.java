package com.hanwha.ai.sourcequality.dto;

public record SourceQualityThresholdRequest(
        Integer cyclomaticComplexity,
        Integer cognitiveComplexity,
        Double duplicateRatio,
        Integer minimumDuplicateLines
) {
}
