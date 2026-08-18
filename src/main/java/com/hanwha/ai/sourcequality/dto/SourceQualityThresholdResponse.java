package com.hanwha.ai.sourcequality.dto;

import com.hanwha.ai.sourcequality.domain.SourceQualityThreshold;

public record SourceQualityThresholdResponse(
        int cyclomaticComplexity,
        int cognitiveComplexity,
        double duplicateRatio,
        int minimumDuplicateLines
) {
    public static SourceQualityThresholdResponse from(SourceQualityThreshold threshold) {
        return new SourceQualityThresholdResponse(
                threshold.getCyclomaticComplexity(), threshold.getCognitiveComplexity(),
                threshold.getDuplicateRatio(), threshold.getMinimumDuplicateLines());
    }
}
