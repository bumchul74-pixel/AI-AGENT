package com.hanwha.ai.sourcegraph.dto;

import java.util.Map;

public record SourceGraphRelationshipResponse(
        String sourceId,
        String targetId,
        String type,
        Map<String, Object> properties
) {
}