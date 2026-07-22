package com.hanwha.ai.rag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RagSearchRequest(
        String query,
        @JsonProperty("top_k") int topK,
        @JsonProperty("projectId") String projectId
) {
    public RagSearchRequest(String query, int topK) {
        this(query, topK, null);
    }

    public RagSearchRequest(String query) {
        this(query, 5, null);
    }
}
