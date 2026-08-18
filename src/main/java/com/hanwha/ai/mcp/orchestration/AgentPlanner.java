package com.hanwha.ai.mcp.orchestration;

import com.hanwha.ai.mcp.router.AgentCapability;
import com.hanwha.ai.mcp.router.AgentRegistry;
import com.hanwha.ai.mcp.router.AgentRegistrySnapshot;
import com.hanwha.ai.mcp.router.AgentRoute;
import com.hanwha.ai.mcp.router.AgentRouter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AgentPlanner {
    private final AgentRegistry registry;
    private final AgentRouter router;

    public AgentPlanner(AgentRegistry registry, AgentRouter router) {
        this.registry = registry;
        this.router = router;
    }

    public AgentPlan plan(String message) {
        AgentRegistrySnapshot snapshot = registry.snapshot();
        List<AgentCapability> roots = snapshot.findMentionedTools(normalize(message));
        if (roots.isEmpty()) {
            AgentRoute route = router.route(message, snapshot);
            AgentCapability capability = route.kind() == AgentRoute.Kind.TOOL_CALL
                    ? snapshot.findByTool(route.target()).orElse(null)
                    : null;
            if (capability == null) {
                return plan(snapshot, List.of(new AgentPlanStep(
                        route.kind().name(), null, route, List.of()
                )));
            }
            Map<String, AgentPlanStep> routedSteps = new LinkedHashMap<>();
            add(capability, message, snapshot, routedSteps, new LinkedHashSet<>());
            routedSteps.put(capability.id(), new AgentPlanStep(
                    capability.id(), capability, route, capability.dependencies()
            ));
            return plan(snapshot, new ArrayList<>(routedSteps.values()));
        }

        Map<String, AgentPlanStep> steps = new LinkedHashMap<>();
        for (AgentCapability root : roots) {
            add(root, message, snapshot, steps, new LinkedHashSet<>());
        }
        return plan(snapshot, new ArrayList<>(steps.values()));
    }

    private AgentPlan plan(AgentRegistrySnapshot snapshot, List<AgentPlanStep> steps) {
        return new AgentPlan(
                steps,
                snapshot.version(),
                snapshot.maxParallelism(),
                snapshot
        );
    }

    private void add(
            AgentCapability capability,
            String message,
            AgentRegistrySnapshot snapshot,
            Map<String, AgentPlanStep> steps,
            Set<String> visiting
    ) {
        if (steps.containsKey(capability.id())) {
            return;
        }
        if (!visiting.add(capability.id())) {
            throw new IllegalStateException("Cyclic agent capability dependency: " + capability.id());
        }
        for (String dependencyId : capability.dependencies()) {
            add(snapshot.requiredCapability(dependencyId), message, snapshot, steps, visiting);
        }
        visiting.remove(capability.id());
        steps.put(capability.id(), new AgentPlanStep(
                capability.id(),
                capability,
                router.routeCapability(capability.id(), message, snapshot),
                capability.dependencies()
        ));
    }

    private String normalize(String message) {
        return message == null ? "" : message.toLowerCase(Locale.ROOT);
    }
}
