package com.hanwha.ai.mcp.orchestration;

import com.hanwha.ai.mcp.router.AgentRoute;
import java.util.List;

public record AgentExecutionResult(
        String executionId,
        Status status,
        List<AgentStepExecution> steps
) {
    public enum Status {
        SUCCEEDED,
        SUCCEEDED_WITH_FALLBACK,
        PARTIAL
    }

    public AgentExecutionResult {
        steps = List.copyOf(steps);
    }

    public AgentRoute route() {
        return steps.getFirst().route();
    }

    public Object result() {
        return steps.size() == 1
                ? steps.getFirst().result()
                : steps.stream().filter(AgentStepExecution::successful).map(AgentStepExecution::result).toList();
    }
}
