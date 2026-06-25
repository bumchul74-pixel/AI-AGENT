package com.hanwha.ai.document.service;

public record StoredDocumentFile(
        String originalFileName,
        String storedFileName,
        String filePath,
        long fileSize,
        String contentType
) {
}
