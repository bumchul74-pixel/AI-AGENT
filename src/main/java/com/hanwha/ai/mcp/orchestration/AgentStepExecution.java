package com.hanwha.ai.mcp.orchestration;

import com.hanwha.ai.mcp.router.AgentRoute;
import java.util.List;

public record AgentStepExecution(
        String stepId,
        String agentId,
        String capabilityId,
        List<String> dependencies,
        AgentRoute route,
        Status status,
        int attempts,
        String fallbackCapabilityId,
        long durationMs,
        Object result,
        String errorType
) {
    public AgentStepExecution {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }

    public enum Status {
        SUCCEEDED,
        FALLBACK_SUCCEEDED,
        FAILED,
        SKIPPED;

        public boolean successful() {
            return this == SUCCEEDED || this == FALLBACK_SUCCEEDED;
        }
    }

    public boolean successful() {
        return status.successful();
    }
}
