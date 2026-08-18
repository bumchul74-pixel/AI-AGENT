package com.hanwha.ai.mcp.domain;

import java.time.LocalDateTime;

public record AgentExecutionHistory(
        String executionId,
        String agentId,
        String capabilityId,
        String configurationVersion,
        String routeKind,
        String target,
        String requestHash,
        String status,
        Long durationMs,
        String errorType,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {
}
