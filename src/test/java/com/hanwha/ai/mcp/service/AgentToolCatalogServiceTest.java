package com.hanwha.ai.mcp.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanwha.ai.mcp.dto.AgentToolCatalogResponse;
import com.hanwha.ai.mcp.router.AgentCapability;
import com.hanwha.ai.mcp.router.AgentRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AgentToolCatalogServiceTest {
    @Test
    void returnsToolsFromTheActiveAgentConfigurationSnapshot() {
        AgentRegistry registry = AgentRegistry.of(
                "database-version-7",
                4,
                List.of(
                        capability("database.search", "search_database_tables", "database-search", 10),
                        capability("security.source", "scan_source", "scan-source", 20)
                )
        );

        AgentToolCatalogResponse response = new AgentToolCatalogService(registry).activeTools();

        assertThat(response.configurationVersion()).isEqualTo("database-version-7");
        assertThat(response.tools()).extracting(AgentToolCatalogResponse.ToolItem::name)
                .containsExactly("scan_source", "search_database_tables");
        assertThat(response.tools().get(0).agentId()).isEqualTo("test-agent");
        assertThat(response.tools().get(0).capabilityId()).isEqualTo("security.source");
        assertThat(response.tools().get(0).inputSchema().required())
                .containsExactly("fileName", "source");
        assertThat(response.tools().get(1).inputSchema().required())
                .containsExactly("keyword");
    }

    private AgentCapability capability(
            String id,
            String tool,
            String resolver,
            int priority
    ) {
        return new AgentCapability(
                "test-agent",
                id,
                "mcp",
                "ai-mcp",
                tool,
                Set.of(),
                resolver,
                priority,
                Duration.ofSeconds(30),
                false
        );
    }
}