package com.hanwha.ai.mcp.router;

import com.hanwha.ai.mcp.config.AgentConfigurationDocument;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

public final class AgentRegistrySnapshot {
    private final String version;
    private final int maxParallelism;
    private final List<AgentCapability> capabilities;

    private AgentRegistrySnapshot(
            String version,
            int maxParallelism,
            List<AgentCapability> capabilities
    ) {
        validate(capabilities);
        validateCycles(capabilities);
        this.version = version;
        this.maxParallelism = Math.max(1, maxParallelism);
        this.capabilities = capabilities.stream()
                .sorted(Comparator.comparingInt(AgentCapability::priority).reversed()
                        .thenComparing(AgentCapability::id))
                .toList();
    }

    public static AgentRegistrySnapshot from(
            String version,
            AgentConfigurationDocument document
    ) {
        if (!StringUtils.hasText(version)) {
            throw new IllegalStateException("Agent configuration version is required.");
        }
        if (document.maxParallelism() <= 0) {
            throw new IllegalStateException("Agent maxParallelism must be greater than zero.");
        }
        List<AgentCapability> capabilities = new ArrayList<>();
        Set<String> agentIds = new HashSet<>();
        for (AgentConfigurationDocument.AgentDefinition agent : document.agents()) {
            if (agent == null) {
                throw new IllegalStateException("Agent definition is required.");
            }
            if (!agent.enabled()) {
                continue;
            }
            requireText(agent.id(), "agent id");
            requireText(agent.name(), "agent name");
            requireText(agent.executor(), "agent executor");
            requireText(agent.server(), "agent server");
            if (!agentIds.add(agent.id())) {
                throw new IllegalStateException("Duplicate agent id: " + agent.id());
            }
            for (AgentConfigurationDocument.CapabilityDefinition capability : agent.capabilities()) {
                if (capability == null) {
                    throw new IllegalStateException(
                            "Capability definition is required for agent: " + agent.id()
                    );
                }
                if (!capability.enabled()) {
                    continue;
                }
                if (capability.timeoutMs() <= 0) {
                    throw new IllegalStateException(
                            "Capability timeoutMs must be greater than zero: " + capability.id()
                    );
                }
                if (capability.maxAttempts() <= 0) {
                    throw new IllegalStateException(
                            "Capability maxAttempts must be greater than zero: " + capability.id()
                    );
                }
                if (capability.retryBackoffMs() < 0) {
                    throw new IllegalStateException(
                            "Capability retryBackoffMs cannot be negative: " + capability.id()
                    );
                }
                capabilities.add(new AgentCapability(
                        agent.id(),
                        capability.id(),
                        agent.executor(),
                        agent.server(),
                        capability.tool(),
                        normalizeIntents(capability.intents()),
                        capability.argumentResolver(),
                        capability.priority(),
                        Duration.ofMillis(capability.timeoutMs()),
                        capability.requiresApproval(),
                        capability.dependencies(),
                        capability.maxAttempts(),
                        Duration.ofMillis(capability.retryBackoffMs()),
                        capability.fallbackCapabilityIds()
                ));
            }
        }
        return new AgentRegistrySnapshot(version, document.maxParallelism(), capabilities);
    }

    public static AgentRegistrySnapshot of(
            String version,
            int maxParallelism,
            List<AgentCapability> capabilities
    ) {
        return new AgentRegistrySnapshot(version, maxParallelism, List.copyOf(capabilities));
    }

    public String version() {
        return version;
    }

    public int maxParallelism() {
        return maxParallelism;
    }

    public List<AgentCapability> capabilities() {
        return capabilities;
    }

    public AgentCapability requiredCapability(String capabilityId) {
        return capabilities.stream()
                .filter(capability -> capability.id().equals(capabilityId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Required agent capability is not configured: " + capabilityId
                ));
    }

    public Optional<AgentCapability> findByTool(String toolName) {
        return capabilities.stream()
                .filter(capability -> capability.tool().equals(toolName))
                .findFirst();
    }

    public Optional<AgentCapability> findByIntent(String intent) {
        String normalized = intent == null ? "" : intent.trim().toLowerCase(Locale.ROOT);
        return capabilities.stream()
                .filter(capability -> capability.intents().contains(normalized))
                .findFirst();
    }

    public Optional<AgentCapability> findMentionedTool(String normalizedMessage) {
        return findMentionedTools(normalizedMessage).stream().findFirst();
    }

    public List<AgentCapability> findMentionedTools(String normalizedMessage) {
        return capabilities.stream()
                .filter(capability -> Pattern.compile(
                        "(?<![a-z0-9_])" + Pattern.quote(capability.tool()) + "(?![a-z0-9_])"
                ).matcher(normalizedMessage).find())
                .toList();
    }

    private static Set<String> normalizeIntents(List<String> intents) {
        Set<String> result = new HashSet<>();
        for (String intent : intents) {
            if (StringUtils.hasText(intent)) {
                result.add(intent.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(result);
    }

    private static void validate(List<AgentCapability> capabilities) {
        if (capabilities.isEmpty()) {
            throw new IllegalStateException("At least one enabled agent capability must be configured.");
        }
        Set<String> ids = new HashSet<>();
        Set<String> tools = new HashSet<>();
        for (AgentCapability capability : capabilities) {
            requireText(capability.agentId(), "agent id");
            requireText(capability.id(), "capability id");
            requireText(capability.executor(), "executor");
            requireText(capability.server(), "server");
            requireText(capability.tool(), "tool");
            requireText(capability.argumentResolver(), "argument resolver");
            if (!ids.add(capability.id())) {
                throw new IllegalStateException("Duplicate agent capability id: " + capability.id());
            }
            if (!tools.add(capability.tool())) {
                throw new IllegalStateException("Duplicate agent capability tool: " + capability.tool());
            }
        }
        for (AgentCapability capability : capabilities) {
            validateReferences(capability.id(), capability.dependencies(), "dependency", ids);
            validateReferences(capability.id(), capability.fallbackCapabilityIds(), "fallback", ids);
        }
    }

    private static void validateCycles(List<AgentCapability> capabilities) {
        for (AgentCapability capability : capabilities) {
            visit(capability.id(), capabilities, new HashSet<>(), new HashSet<>());
        }
    }

    private static void visit(
            String capabilityId,
            List<AgentCapability> capabilities,
            Set<String> visiting,
            Set<String> visited
    ) {
        if (visited.contains(capabilityId)) {
            return;
        }
        if (!visiting.add(capabilityId)) {
            throw new IllegalStateException("Cyclic agent capability dependency: " + capabilityId);
        }
        AgentCapability capability = capabilities.stream()
                .filter(candidate -> candidate.id().equals(capabilityId))
                .findFirst()
                .orElseThrow();
        for (String dependency : capability.dependencies()) {
            visit(dependency, capabilities, visiting, visited);
        }
        visiting.remove(capabilityId);
        visited.add(capabilityId);
    }

    private static void validateReferences(
            String capabilityId,
            List<String> references,
            String label,
            Set<String> knownIds
    ) {
        for (String reference : references) {
            requireText(reference, label + " capability id");
            if (capabilityId.equals(reference)) {
                throw new IllegalStateException(
                        "Capability cannot reference itself as " + label + ": " + capabilityId
                );
            }
            if (!knownIds.contains(reference)) {
                throw new IllegalStateException(
                        "Unknown " + label + " capability for " + capabilityId + ": " + reference
                );
            }
        }
    }

    private static void requireText(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("Agent capability " + label + " is required.");
        }
    }
}