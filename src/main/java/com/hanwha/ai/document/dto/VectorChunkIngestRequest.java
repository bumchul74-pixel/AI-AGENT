package com.hanwha.ai.document.dto;

import java.util.List;
import java.util.Map;

public record VectorChunkIngestRequest(List<VectorChunk> chunks) {
    public VectorChunkIngestRequest {
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }

    public record VectorChunk(
            String chunkId,
            String sourceKey,
            String content,
            Long documentId,
            String projectId,
            String filePath,
            String fileHash,
            List<String> entityIds,
            String symbol,
            Map<String, Object> metadata
    ) {
        public VectorChunk {
            entityIds = entityIds == null ? List.of() : List.copyOf(entityIds);
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }
}
