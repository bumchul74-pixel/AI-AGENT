package com.hanwha.ai.neo4jexplorer.dto;

import java.util.List;
import java.util.Map;

public record Neo4jRelationshipResponse(
        String elementId,
        String type,
        String direction,
        Map<String, Object> properties,
        String otherElementId,
        List<String> otherLabels,
        String otherDisplayName
) {
    public Neo4jRelationshipResponse {
        properties = properties == null ? Map.of() : Map.copyOf(properties);
        otherLabels = otherLabels == null ? List.of() : List.copyOf(otherLabels);
    }
}