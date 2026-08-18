package com.hanwha.ai.mcp.config;

import java.util.List;

public record AgentConfigurationDocument(
        int maxParallelism,
        List<AgentDefinition> agents
) {
    public AgentConfigurationDocument {
        agents = agents == null ? List.of() : List.copyOf(agents);
    }

    public record AgentDefinition(
            String id,
            String name,
            boolean enabled,
            String executor,
            String server,
            List<CapabilityDefinition> capabilities
    ) {
        public AgentDefinition {
            capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        }
    }

    public record CapabilityDefinition(
            String id,
            String tool,
            boolean enabled,
            List<String> intents,
            String argumentResolver,
            int priority,
            long timeoutMs,
            boolean requiresApproval,
            List<String> dependencies,
            int maxAttempts,
            long retryBackoffMs,
            List<String> fallbackCapabilityIds
    ) {
        public CapabilityDefinition {
            intents = intents == null ? List.of() : List.copyOf(intents);
            dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
            fallbackCapabilityIds = fallbackCapabilityIds == null
                    ? List.of()
                    : List.copyOf(fallbackCapabilityIds);
        }
    }
}