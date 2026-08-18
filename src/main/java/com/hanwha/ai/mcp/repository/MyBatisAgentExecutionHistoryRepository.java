package com.hanwha.ai.mcp.repository;

import com.hanwha.ai.mcp.domain.AgentExecutionHistory;
import com.hanwha.ai.mcp.mapper.AgentExecutionHistoryMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisAgentExecutionHistoryRepository implements AgentExecutionHistoryRepository {
    private final AgentExecutionHistoryMapper mapper;

    public MyBatisAgentExecutionHistoryRepository(AgentExecutionHistoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void recordStarted(AgentExecutionHistory history) {
        mapper.insert(history);
    }

    @Override
    public void recordSucceeded(String executionId, LocalDateTime completedAt, long durationMs) {
        if (mapper.markSucceeded(executionId, completedAt, durationMs) != 1) {
            throw new IllegalStateException("Agent execution history success update was not applied");
        }
    }

    @Override
    public void recordFailed(
            String executionId,
            LocalDateTime completedAt,
            long durationMs,
            String errorType,
            String errorMessage
    ) {
        if (mapper.markFailed(executionId, completedAt, durationMs, errorType, errorMessage) != 1) {
            throw new IllegalStateException("Agent execution history failure update was not applied");
        }
    }
}
