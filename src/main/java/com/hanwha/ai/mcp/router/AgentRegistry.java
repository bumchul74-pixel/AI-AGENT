package com.hanwha.ai.mcp.router;

import com.hanwha.ai.mcp.config.AgentConfigurationBootstrapLoader;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AgentRegistry {
    private final AtomicReference<AgentRegistrySnapshot> current;

    @Autowired
    public AgentRegistry(AgentConfigurationBootstrapLoader bootstrapLoader) {
        this(AgentRegistrySnapshot.from(
                AgentConfigurationBootstrapLoader.BOOTSTRAP_VERSION,
                bootstrapLoader.load()
        ));
    }

    private AgentRegistry(AgentRegistrySnapshot initialSnapshot) {
        this.current = new AtomicReference<>(initialSnapshot);
    }

    public static AgentRegistry of(List<AgentCapability> capabilities) {
        return new AgentRegistry(AgentRegistrySnapshot.of("test", 4, capabilities));
    }

    public static AgentRegistry of(
            String version,
            int maxParallelism,
            List<AgentCapability> capabilities
    ) {
        return new AgentRegistry(AgentRegistrySnapshot.of(version, maxParallelism, capabilities));
    }

    public AgentRegistrySnapshot snapshot() {
        return current.get();
    }

    public void publish(AgentRegistrySnapshot snapshot) {
        current.set(Objects.requireNonNull(snapshot, "snapshot"));
    }

    public String version() {
        return snapshot().version();
    }

    public int maxParallelism() {
        return snapshot().maxParallelism();
    }

    public List<AgentCapability> capabilities() {
        return snapshot().capabilities();
    }

    public AgentCapability requiredCapability(String capabilityId) {
        return snapshot().requiredCapability(capabilityId);
    }

    public Optional<AgentCapability> findByTool(String toolName) {
        return snapshot().findByTool(toolName);
    }

    public Optional<AgentCapability> findByIntent(String intent) {
        return snapshot().findByIntent(intent);
    }

    public Optional<AgentCapability> findMentionedTool(String normalizedMessage) {
        return snapshot().findMentionedTool(normalizedMessage);
    }

    public List<AgentCapability> findMentionedTools(String normalizedMessage) {
        return snapshot().findMentionedTools(normalizedMessage);
    }
}
