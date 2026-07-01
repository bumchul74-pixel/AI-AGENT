package com.hanwha.ai.sourcegraph.dto;

import com.hanwha.ai.sourcegraph.domain.SourceGraphIndexStatus;
import java.time.LocalDateTime;

public record SourceGraphIndexResult(
        SourceGraphIndexStatus status,
        LocalDateTime indexedAt,
        String errorMessage
) {
    public static SourceGraphIndexResult success(LocalDateTime indexedAt) {
        return new SourceGraphIndexResult(SourceGraphIndexStatus.SUCCESS, indexedAt, null);
    }

    public static SourceGraphIndexResult failed(LocalDateTime indexedAt, String errorMessage) {
        return new SourceGraphIndexResult(SourceGraphIndexStatus.FAILED, indexedAt, errorMessage);
    }

    public static SourceGraphIndexResult skipped(LocalDateTime indexedAt, String message) {
        return new SourceGraphIndexResult(SourceGraphIndexStatus.SKIPPED, indexedAt, message);
    }
}