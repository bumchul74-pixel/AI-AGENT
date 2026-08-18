package com.hanwha.ai.sourcequality.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SourceQualityDashboardResponse(
        String projectKey,
        LocalDateTime evaluatedAt,
        SourceQualitySummaryResponse summary,
        SourceQualityThresholdResponse thresholds,
        SourceQualityGateResponse gate,
        List<SourceQualityDuplicateGroupResponse> duplicateGroups,
        List<SourceQualityMethodResponse> highComplexityMethods,
        List<SourceQualityTrendResponse> trend
) {
}
