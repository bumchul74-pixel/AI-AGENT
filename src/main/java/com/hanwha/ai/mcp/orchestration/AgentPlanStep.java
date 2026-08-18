package com.hanwha.ai.mcp.orchestration;

import com.hanwha.ai.mcp.router.AgentCapability;
import com.hanwha.ai.mcp.router.AgentRoute;
import java.util.List;

public record AgentPlanStep(
        String id,
        AgentCapability capability,
        AgentRoute route,
        List<String> dependencies
) {
    public AgentPlanStep {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}