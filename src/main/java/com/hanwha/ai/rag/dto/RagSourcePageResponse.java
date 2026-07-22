package com.hanwha.ai.rag.dto;

import java.util.List;

public record RagSourcePageResponse(
        List<RagSourceResponse> sources,
        int page,
        int size,
        long totalCount,
        boolean hasNext
) {
    public RagSourcePageResponse {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
