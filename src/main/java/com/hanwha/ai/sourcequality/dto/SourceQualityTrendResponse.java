package com.hanwha.ai.sourcequality.dto;

import com.hanwha.ai.sourcequality.domain.SourceQualitySnapshot;
import java.time.LocalDateTime;

public record SourceQualityTrendResponse(
        Long id,
        int totalMethodCount,
        int duplicateMethodCount,
        double duplicateRatio,
        int highComplexityCount,
        int maxCyclomaticComplexity,
        int maxCognitiveComplexity,
        String gateStatus,
        LocalDateTime createdAt
) {
    public static SourceQualityTrendResponse from(SourceQualitySnapshot snapshot) {
        return new SourceQualityTrendResponse(
                snapshot.getId(), snapshot.getTotalMethodCount(), snapshot.getDuplicateMethodCount(),
                snapshot.getDuplicateRatio(), snapshot.getHighComplexityCount(),
                snapshot.getMaxCyclomaticComplexity(), snapshot.getMaxCognitiveComplexity(),
                snapshot.getGateStatus(), snapshot.getCreatedAt());
    }
}
