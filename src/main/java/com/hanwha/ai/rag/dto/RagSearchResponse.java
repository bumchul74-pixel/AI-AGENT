package com.hanwha.ai.rag.dto;

import java.util.List;

public record RagSearchResponse(List<String> documents, List<RagChunkResult> chunks) {
    public RagSearchResponse {
        documents = documents == null ? List.of() : List.copyOf(documents);
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }

    public RagSearchResponse(List<String> documents) {
        this(documents, List.of());
    }

    public static RagSearchResponse empty() {
        return new RagSearchResponse(List.of(), List.of());
    }
}
