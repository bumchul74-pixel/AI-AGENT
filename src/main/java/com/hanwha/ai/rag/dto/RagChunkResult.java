package com.hanwha.ai.rag.dto;

import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

public record RagChunkResult(
        String chunkId,
        String sourceKey,
        String content,
        List<String> entityIds,
        double score,
        String filePath,
        Map<String, Object> metadata
) {
    public RagChunkResult {
        entityIds = entityIds == null ? List.of() : List.copyOf(entityIds);
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
