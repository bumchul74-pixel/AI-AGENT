package com.hanwha.ai.mcp.repository;

import com.hanwha.ai.mcp.domain.AgentExecutionHistory;
import java.time.LocalDateTime;

public interface AgentExecutionHistoryRepository {
    void recordStarted(AgentExecutionHistory history);

    void recordSucceeded(String executionId, LocalDateTime completedAt, long durationMs);

    void recordFailed(
            String executionId,
            LocalDateTime completedAt,
            long durationMs,
            String errorType,
            String errorMessage
    );
}
