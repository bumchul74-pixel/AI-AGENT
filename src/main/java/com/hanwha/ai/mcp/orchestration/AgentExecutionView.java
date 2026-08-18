package com.hanwha.ai.mcp.orchestration;

import java.util.List;

public record AgentExecutionView(
        String executionId,
        String status,
        List<AgentExecutionStepView> steps
) {
    public AgentExecutionView {
        steps = List.copyOf(steps);
    }

    public static AgentExecutionView from(AgentExecutionResult result) {
        return new AgentExecutionView(
                result.executionId(),
                result.status().name(),
                result.steps().stream().map(AgentExecutionStepView::from).toList()
        );
    }
}