package com.hanwha.ai.systemstatus.dto;

import com.hanwha.ai.systemstatus.domain.SystemHealthStatus;
import java.time.Instant;
import java.util.List;

public record SystemStatusResponse(
        SystemHealthStatus status,
        Instant checkedAt,
        int totalCount,
        int upCount,
        int degradedCount,
        int downCount,
        int unknownCount,
        List<SystemDependencyStatusResponse> systems
) {
}
