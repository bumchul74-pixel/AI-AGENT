package com.hanwha.ai.mcp.mapper;

import com.hanwha.ai.mcp.domain.AgentExecutionHistory;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentExecutionHistoryMapper {
    void insert(AgentExecutionHistory history);

    int markSucceeded(
            @Param("executionId") String executionId,
            @Param("completedAt") LocalDateTime completedAt,
            @Param("durationMs") long durationMs
    );

    int markFailed(
            @Param("executionId") String executionId,
            @Param("completedAt") LocalDateTime completedAt,
            @Param("durationMs") long durationMs,
            @Param("errorType") String errorType,
            @Param("errorMessage") String errorMessage
    );
}
