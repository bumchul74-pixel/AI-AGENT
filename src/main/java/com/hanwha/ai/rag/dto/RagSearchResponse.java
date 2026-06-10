package com.hanwha.ai.rag.dto;

import java.util.List;

public record RagSearchResponse(List<String> documents) {
    public static RagSearchResponse empty() {
        return new RagSearchResponse(List.of());
    }
}
