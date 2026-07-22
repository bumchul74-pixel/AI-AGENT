package com.hanwha.ai.document.dto;

import com.hanwha.ai.document.domain.RagDocument;
import java.time.LocalDateTime;

public record DocumentResponse(
        Long id,
        String projectKey,
        String originalFileName,
        Long fileSize,
        String contentType,
        String documentType,
        String fileHash,
        String vectorSourceKey,
        String graphSourceKey,
        String indexStatus,
        String vectorIndexStatus,
        String graphIndexStatus,
        Integer chunkCount,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public DocumentResponse(
            Long id, String originalFileName, Long fileSize, String contentType, String documentType,
            String fileHash, String vectorSourceKey, String graphSourceKey, String indexStatus,
            String vectorIndexStatus, String graphIndexStatus, Integer chunkCount, String errorMessage,
            LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        this(id, "default", originalFileName, fileSize, contentType, documentType, fileHash,
                vectorSourceKey, graphSourceKey, indexStatus, vectorIndexStatus, graphIndexStatus,
                chunkCount, errorMessage, createdAt, updatedAt);
    }

    public static DocumentResponse from(RagDocument document) {
        return new DocumentResponse(
                document.getId(),
                document.getProjectKey(),
                document.getOriginalFileName(),
                document.getFileSize(),
                document.getContentType(),
                document.getDocumentType(),
                document.getFileHash(),
                document.getVectorSourceKey(),
                document.getGraphSourceKey(),
                document.getIndexStatus(),
                document.getVectorIndexStatus(),
                document.getGraphIndexStatus(),
                document.getChunkCount(),
                document.getErrorMessage(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
