package com.hanwha.ai.sourcequality.dto;

import java.util.List;

public record SourceQualityDuplicateGroupResponse(
        String type,
        String hash,
        int methodCount,
        int duplicatedLineCount,
        List<SourceQualityMethodResponse> methods
) {
}
