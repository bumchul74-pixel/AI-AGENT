package com.hanwha.ai.securecoding.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ProjectSecureCodingScanResponse(
        String projectKey,
        LocalDateTime scannedAt,
        int totalFiles,
        int scannedFiles,
        int passedFiles,
        int findingCount,
        int errorFiles,
        List<SecureCodingResultRow> results
) {
}
