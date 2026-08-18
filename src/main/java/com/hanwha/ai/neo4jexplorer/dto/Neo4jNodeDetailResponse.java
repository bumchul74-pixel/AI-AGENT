package com.hanwha.ai.neo4jexplorer.dto;

import java.util.List;
import java.util.Map;

public record Neo4jNodeDetailResponse(
        String elementId,
        List<String> labels,
        String displayName,
        Map<String, Object> properties,
        long relationshipCount,
        List<Neo4jRelationshipResponse> relationships
) {
    public Neo4jNodeDetailResponse {
        labels = labels == null ? List.of() : List.copyOf(labels);
        properties = properties == null ? Map.of() : Map.copyOf(properties);
        relationships = relationships == null ? List.of() : List.copyOf(relationships);
    }
}