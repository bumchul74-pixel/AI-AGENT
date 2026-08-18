package com.hanwha.ai.sourcequality.dto;

import java.util.List;

public record SourceQualityDuplicateGroupDetailResponse(
        String type,
        String hash,
        int methodCount,
        List<SourceQualityMethodDetailResponse> methods
) {
}
