package com.hanwha.ai.sourcegraph.dto;

import java.util.Map;

public record SourceGraphNodeResponse(
        String id,
        String label,
        String name,
        Map<String, Object> properties
) {
}