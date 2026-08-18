package com.hanwha.ai.mcp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hanwha.ai.mcp.gateway.AiMcpGatewayService;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class McpToolConfigurationSynchronizerTest {
    @Test
    void detectsAddsAndRemovalsThenSavesAndActivatesWithoutApproval() {
        AgentConfigurationDocument current = document(
                capability("keep", "keep_tool", List.of("remove")),
                capability("remove", "removed_tool", List.of())
        );
        AgentConfigurationService configurationService = mock(AgentConfigurationService.class);
        when(configurationService.active())
                .thenReturn(new AgentConfigurationView("version-1", "DATABASE", current));
        when(configurationService.saveAndActivate(
                org.mockito.ArgumentMatchers.any(), eq("mcp-auto-sync")
        )).thenAnswer(invocation -> new AgentConfigurationView(
                "version-2", "DATABASE", invocation.getArgument(0)
        ));
        AiMcpGatewayService gatewayService = mock(AiMcpGatewayService.class);
        when(gatewayService.listTools()).thenReturn(new McpSchema.ListToolsResult(
                List.of(tool("keep_tool"), tool("new_tool")), null
        ));
        McpToolConfigurationSynchronizer synchronizer =
                new McpToolConfigurationSynchronizer(
                        new AgentOrchestrationProperties(), gatewayService, configurationService
                );

        assertThat(synchronizer.synchronizeNow()).isTrue();

        ArgumentCaptor<AgentConfigurationDocument> captor =
                ArgumentCaptor.forClass(AgentConfigurationDocument.class);
        verify(configurationService).saveAndActivate(captor.capture(), eq("mcp-auto-sync"));
        AgentConfigurationDocument saved = captor.getValue();
        assertThat(saved.agents()).flatExtracting(
                AgentConfigurationDocument.AgentDefinition::capabilities
        ).extracting(AgentConfigurationDocument.CapabilityDefinition::tool)
                .containsExactlyInAnyOrder("keep_tool", "new_tool");
        AgentConfigurationDocument.CapabilityDefinition kept = saved.agents().stream()
                .flatMap(agent -> agent.capabilities().stream())
                .filter(capability -> capability.tool().equals("keep_tool"))
                .findFirst().orElseThrow();
        assertThat(kept.dependencies()).isEmpty();
        assertThat(saved.agents()).anyMatch(
                agent -> agent.id().equals("auto-discovered-agent")
        );
    }

    @Test
    void doesNotCreateANewVersionWhenToolsAreAlreadySynchronized() {
        AgentConfigurationDocument current = document(
                capability("keep", "keep_tool", List.of())
        );
        AgentConfigurationService configurationService = mock(AgentConfigurationService.class);
        when(configurationService.active())
                .thenReturn(new AgentConfigurationView("version-1", "DATABASE", current));
        AiMcpGatewayService gatewayService = mock(AiMcpGatewayService.class);
        when(gatewayService.listTools()).thenReturn(new McpSchema.ListToolsResult(
                List.of(tool("keep_tool")), null
        ));
        McpToolConfigurationSynchronizer synchronizer =
                new McpToolConfigurationSynchronizer(
                        new AgentOrchestrationProperties(), gatewayService, configurationService
                );

        assertThat(synchronizer.synchronizeNow()).isFalse();
    }

    private McpSchema.Tool tool(String name) {
        return McpSchema.Tool.builder().name(name).description(name)
                .inputSchema(java.util.Map.of("type", "object")).build();
    }

    private AgentConfigurationDocument document(
            AgentConfigurationDocument.CapabilityDefinition... capabilities
    ) {
        return new AgentConfigurationDocument(4, List.of(
                new AgentConfigurationDocument.AgentDefinition(
                        "test-agent", "Test Agent", true, "mcp", "ai-mcp",
                        List.of(capabilities)
                )
        ));
    }

    private AgentConfigurationDocument.CapabilityDefinition capability(
            String id, String tool, List<String> dependencies
    ) {
        return new AgentConfigurationDocument.CapabilityDefinition(
                id, tool, true, List.of(), "none", 10, 30_000, false,
                dependencies, 1, 100, List.of()
        );
    }
}