package com.hanwha.ai.mcp.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AgentRegistryTest {
    @Test
    void publishesImmutableCapabilitiesOrderedByPriority() {
        AgentRegistry registry = AgentRegistry.of("v1", 3, List.of(
                capability("source.low", "low_tool", "low-intent", 10),
                capability("source.high", "high_tool", "high-intent", 100)
        ));

        assertThat(registry.version()).isEqualTo("v1");
        assertThat(registry.maxParallelism()).isEqualTo(3);
        assertThat(registry.capabilities()).extracting(AgentCapability::id)
                .containsExactly("source.high", "source.low");
        assertThat(registry.findByIntent("high-intent")).get().extracting(AgentCapability::tool)
                .isEqualTo("high_tool");
    }

    @Test
    void replacesTheWholeSnapshotAtomically() {
        AgentRegistry registry = AgentRegistry.of(List.of(
                capability("source.one", "one_tool", "one", 10)
        ));
        AgentRegistrySnapshot replacement = AgentRegistrySnapshot.of(
                "v2",
                2,
                List.of(capability("source.two", "two_tool", "two", 20))
        );

        registry.publish(replacement);

        assertThat(registry.version()).isEqualTo("v2");
        assertThat(registry.findByTool("one_tool")).isEmpty();
        assertThat(registry.findByTool("two_tool")).isPresent();
    }

    @Test
    void rejectsDuplicateToolRegistrations() {
        assertThatThrownBy(() -> AgentRegistry.of(List.of(
                capability("source.one", "same_tool", "one", 10),
                capability("source.two", "same_tool", "two", 20)
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate agent capability tool", "same_tool");
    }

    private AgentCapability capability(
            String id,
            String tool,
            String intent,
            int priority
    ) {
        return new AgentCapability(
                "source-agent",
                id,
                "mcp",
                "ai-mcp",
                tool,
                Set.of(intent),
                "none",
                priority,
                Duration.ofSeconds(30),
                false
        );
    }
}
