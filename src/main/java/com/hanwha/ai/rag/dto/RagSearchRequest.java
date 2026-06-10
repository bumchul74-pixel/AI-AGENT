package com.hanwha.ai.rag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RagSearchRequest(
        String query,
        @JsonProperty("top_k") int topK
) {
    public RagSearchRequest(String query) {
        this(query, 5);
    }
}
