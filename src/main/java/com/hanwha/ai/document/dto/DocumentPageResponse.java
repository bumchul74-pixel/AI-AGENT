package com.hanwha.ai.document.dto;

import java.util.List;

public record DocumentPageResponse(
        List<DocumentResponse> documents,
        int page,
        int size,
        long totalCount,
        boolean hasNext
) {
    public DocumentPageResponse {
        documents = documents == null ? List.of() : List.copyOf(documents);
    }
}