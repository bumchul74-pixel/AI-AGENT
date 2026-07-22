package com.hanwha.ai.document.dto;

import java.util.List;

public record ProjectArchiveUploadResponse(
        String archiveName,
        int discoveredFiles,
        int indexedFiles,
        int skippedFiles,
        int failedFiles,
        List<DocumentResponse> documents,
        List<ArchiveFailure> failures
) {
    public record ArchiveFailure(String entryPath, String message) {
    }
}
