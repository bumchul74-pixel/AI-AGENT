package com.hanwha.ai.document.dto;

import com.hanwha.ai.document.domain.RagDocument;
import java.time.LocalDateTime;

public record DocumentResponse(
        Long id,
        String originalFileName,
        Long fileSize,
        String contentType,
        String documentType,
        String indexStatus,
        Integer chunkCount,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static DocumentResponse from(RagDocument document) {
        return new DocumentResponse(
                document.getId(),
                document.getOriginalFileName(),
                document.getFileSize(),
                document.getContentType(),
                document.getDocumentType(),
                document.getIndexStatus(),
                document.getChunkCount(),
                document.getErrorMessage(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
