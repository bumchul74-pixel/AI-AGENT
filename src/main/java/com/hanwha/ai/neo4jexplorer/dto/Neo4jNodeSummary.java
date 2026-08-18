package com.hanwha.ai.neo4jexplorer.dto;

import java.util.List;

public record Neo4jNodeSummary(
        String elementId,
        List<String> labels,
        String displayName,
        int propertyCount,
        long relationshipCount
) {
    public Neo4jNodeSummary {
        labels = labels == null ? List.of() : List.copyOf(labels);
    }
}