package com.hanwha.ai.mcp.router;

import java.time.Duration;
import java.util.List;
import java.util.Set;

public record AgentCapability(
        String agentId,
        String id,
        String executor,
        String server,
        String tool,
        Set<String> intents,
        String argumentResolver,
        int priority,
        Duration timeout,
        boolean requiresApproval,
        List<String> dependencies,
        int maxAttempts,
        Duration retryBackoff,
        List<String> fallbackCapabilityIds
) {
    public AgentCapability {
        intents = intents == null ? Set.of() : Set.copyOf(intents);
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        maxAttempts = Math.max(1, maxAttempts);
        retryBackoff = retryBackoff == null ? Duration.ofMillis(100) : retryBackoff;
        fallbackCapabilityIds = fallbackCapabilityIds == null ? List.of() : List.copyOf(fallbackCapabilityIds);
    }

    public AgentCapability(
            String agentId,
            String id,
            String executor,
            String server,
            String tool,
            Set<String> intents,
            String argumentResolver,
            int priority,
            Duration timeout,
            boolean requiresApproval
    ) {
        this(agentId, id, executor, server, tool, intents, argumentResolver, priority, timeout,
                requiresApproval, List.of(), 1, Duration.ofMillis(100), List.of());
    }
}
