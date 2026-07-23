package com.hanwha.ai.securecoding.domain;

import java.time.LocalDateTime;

public record SecureCodingScanJob(
        Long id, String projectKey, String status,
        int totalFiles, int processedFiles, int passedFiles,
        int findingCount, int errorFiles, String errorMessage,
        LocalDateTime createdAt, LocalDateTime startedAt,
        LocalDateTime completedAt, LocalDateTime updatedAt
) {
}
