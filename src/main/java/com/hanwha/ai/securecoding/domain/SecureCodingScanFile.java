package com.hanwha.ai.securecoding.domain;

public record SecureCodingScanFile(
        Long id, Long jobId, Long documentId,
        String fileName, String fileType, String status,
        String errorMessage
) {
}
