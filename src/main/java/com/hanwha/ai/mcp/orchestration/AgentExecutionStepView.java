package com.hanwha.ai.mcp.orchestration;

import java.util.List;

public record AgentExecutionStepView(
        String stepId,
        String agentId,
        String capabilityId,
        String routeKind,
        String target,
        List<String> dependencies,
        String status,
        int attempts,
        String fallbackCapabilityId,
        long durationMs,
        String errorType
) {
    public AgentExecutionStepView {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }

    static AgentExecutionStepView from(AgentStepExecution step) {
        String safeTarget = step.route().kind() == com.hanwha.ai.mcp.router.AgentRoute.Kind.TOOL_CALL
                ? step.route().target()
                : step.route().kind().name();
        return new AgentExecutionStepView(
                step.stepId(),
                step.agentId(),
                step.capabilityId(),
                step.route().kind().name(),
                safeTarget,
                step.dependencies(),
                step.status().name(),
                step.attempts(),
                step.fallbackCapabilityId(),
                step.durationMs(),
                step.errorType()
        );
    }
}