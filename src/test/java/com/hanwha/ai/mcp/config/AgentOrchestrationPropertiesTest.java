package com.hanwha.ai.mcp.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class AgentOrchestrationPropertiesTest {
    @Test
    void enablesTheLocalManagementApiByDefault() {
        assertThat(new AgentOrchestrationProperties().isAdminApiEnabled()).isTrue();
    }

    @Test
    void bindsDatabaseBootstrapAndRefreshSettingsOnly() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.ofEntries(
                Map.entry("agent.orchestration.config.database-enabled", "true"),
                Map.entry("agent.orchestration.config.seed-enabled", "false"),
                Map.entry("agent.orchestration.config.fallback-on-startup", "false"),
                Map.entry("agent.orchestration.config.refresh-mode", "manual"),
                Map.entry("agent.orchestration.config.refresh-interval-ms", "45000"),
                Map.entry(
                        "agent.orchestration.config.bootstrap-location",
                        "classpath:test-agent-bootstrap.json"
                ),
                Map.entry("agent.orchestration.config.admin-api-enabled", "true")
        ));

        AgentOrchestrationProperties properties = new Binder(source)
                .bind(
                        "agent.orchestration.config",
                        Bindable.of(AgentOrchestrationProperties.class)
                )
                .orElseThrow(() -> new IllegalStateException(
                        "Agent orchestration properties were not bound"
                ));

        assertThat(properties.isDatabaseEnabled()).isTrue();
        assertThat(properties.isSeedEnabled()).isFalse();
        assertThat(properties.isFallbackOnStartup()).isFalse();
        assertThat(properties.getRefreshMode())
                .isEqualTo(AgentOrchestrationProperties.RefreshMode.MANUAL);
        assertThat(properties.getRefreshIntervalMs()).isEqualTo(45_000);
        assertThat(properties.getBootstrapLocation())
                .isEqualTo("classpath:test-agent-bootstrap.json");
        assertThat(properties.isAdminApiEnabled()).isTrue();
    }
}
