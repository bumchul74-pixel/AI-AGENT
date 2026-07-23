package com.hanwha.ai.securecoding.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SecureCodingScanJobResponse(
        Long jobId, String projectKey, String status, String message,
        int progressPercent, int totalFiles, int scannedFiles,
        int passedFiles, int findingCount, int errorFiles,
        LocalDateTime requestedAt, LocalDateTime startedAt, LocalDateTime scannedAt,
        List<SecureCodingResultRow> results
) {
}
