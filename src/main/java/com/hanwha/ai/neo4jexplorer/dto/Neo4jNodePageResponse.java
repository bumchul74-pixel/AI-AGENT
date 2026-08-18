package com.hanwha.ai.neo4jexplorer.dto;

import java.util.List;

public record Neo4jNodePageResponse(
        List<Neo4jNodeSummary> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public Neo4jNodePageResponse {
        content = content == null ? List.of() : List.copyOf(content);
    }
}