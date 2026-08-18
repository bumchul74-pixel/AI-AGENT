package com.hanwha.ai.mcp.orchestration;

import com.hanwha.ai.mcp.router.AgentRegistrySnapshot;
import java.util.List;

public record AgentPlan(
        List<AgentPlanStep> steps,
        String configurationVersion,
        int maxParallelism,
        AgentRegistrySnapshot snapshot
) {
    public AgentPlan {
        steps = List.copyOf(steps);
        maxParallelism = Math.max(1, maxParallelism);
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("Agent plan must contain at least one step.");
        }
    }

    public AgentPlan(List<AgentPlanStep> steps) {
        this(steps, "legacy", 4, null);
    }
}
