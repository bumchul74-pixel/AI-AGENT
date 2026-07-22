package com.hanwha.ai.rag.dto;

public record RagSourceResponse(
        String sourceKey,
        Long documentId,
        String projectId,
        String filePath,
        String fileHash,
        int chunkCount,
        boolean vectorTracked,
        boolean graphTracked,
        String graphKey,
        String fileName,
        int graphNodeCount
) {
}
