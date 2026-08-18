package com.hanwha.ai.systemstatus.dto;

import com.hanwha.ai.systemstatus.domain.SystemHealthStatus;
import java.time.Instant;

public record SystemDependencyStatusResponse(
        String id,
        String name,
        String category,
        SystemHealthStatus status,
        boolean critical,
        String checkType,
        long latencyMs,
        Instant checkedAt,
        String message
) {
}
