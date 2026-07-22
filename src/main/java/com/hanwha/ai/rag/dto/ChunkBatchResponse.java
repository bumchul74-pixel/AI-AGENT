package com.hanwha.ai.rag.dto;

import java.util.List;

public record ChunkBatchResponse(List<RagChunkResult> chunks) {
    public ChunkBatchResponse {
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }
}
