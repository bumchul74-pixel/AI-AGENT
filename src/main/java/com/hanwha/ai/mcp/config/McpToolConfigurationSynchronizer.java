package com.hanwha.ai.mcp.config;

import com.hanwha.ai.mcp.config.AgentConfigurationDocument.AgentDefinition;
import com.hanwha.ai.mcp.config.AgentConfigurationDocument.CapabilityDefinition;
import com.hanwha.ai.mcp.gateway.AiMcpGatewayService;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(prefix = "spring.ai.mcp.client", name = "enabled", havingValue = "true")
public class McpToolConfigurationSynchronizer {
    private static final Logger log =
            LoggerFactory.getLogger(McpToolConfigurationSynchronizer.class);
    private static final String AUTO_AGENT_ID = "auto-discovered-agent";

    private final AgentOrchestrationProperties properties;
    private final AiMcpGatewayService gatewayService;
    private final AgentConfigurationService configurationService;

    public McpToolConfigurationSynchronizer(
            AgentOrchestrationProperties properties,
            AiMcpGatewayService gatewayService,
            AgentConfigurationService configurationService
    ) {
        this.properties = properties;
        this.gatewayService = gatewayService;
        this.configurationService = configurationService;
    }

    @Scheduled(
            fixedDelayString = "${agent.orchestration.config.tool-sync-interval-ms:30000}",
            initialDelayString = "${agent.orchestration.config.tool-sync-initial-delay-ms:30000}"
    )
    public void synchronizeOnSchedule() {
        if (!properties.isToolSyncEnabled() || !properties.isDatabaseEnabled()) {
            return;
        }
        try {
            synchronizeNow();
        } catch (RuntimeException exception) {
            log.warn(
                    "MCP Tool automatic synchronization failed. Keeping current Snapshot. "
                            + "errorType={}",
                    exception.getClass().getSimpleName()
            );
        }
    }

    public boolean synchronizeNow() {
        McpSchema.ListToolsResult result = gatewayService.listTools();
        List<McpSchema.Tool> liveTools = result == null || result.tools() == null
                ? List.of()
                : result.tools();
        if (liveTools.isEmpty()) {
            throw new IllegalStateException(
                    "MCP Tool synchronization rejected an empty tools/list response."
            );
        }
        AgentConfigurationView active = configurationService.active();
        AgentConfigurationDocument reconciled = reconcile(active.configuration(), liveTools);
        if (reconciled.equals(active.configuration())) {
            return false;
        }
        AgentConfigurationView saved = configurationService.saveAndActivate(
                reconciled,
                "mcp-auto-sync"
        );
        log.info(
                "MCP Tools automatically synchronized and activated. previousVersion={}, "
                        + "newVersion={}, toolCount={}",
                active.version(),
                saved.version(),
                liveTools.size()
        );
        return true;
    }

    AgentConfigurationDocument reconcile(
            AgentConfigurationDocument current,
            List<McpSchema.Tool> liveTools
    ) {
        Set<String> liveNames = new LinkedHashSet<>();
        for (McpSchema.Tool tool : liveTools) {
            if (tool != null && StringUtils.hasText(tool.name())) {
                liveNames.add(tool.name().trim());
            }
        }
        if (liveNames.isEmpty()) {
            throw new IllegalStateException("MCP Tool synchronization requires named tools.");
        }

        Set<String> retainedCapabilityIds = new HashSet<>();
        Set<String> configuredTools = new HashSet<>();
        for (AgentDefinition agent : current.agents()) {
            for (CapabilityDefinition capability : agent.capabilities()) {
                if (liveNames.contains(capability.tool())) {
                    retainedCapabilityIds.add(capability.id());
                    configuredTools.add(capability.tool());
                }
            }
        }

        List<AgentDefinition> agents = new ArrayList<>();
        AgentDefinition autoAgent = null;
        for (AgentDefinition agent : current.agents()) {
            List<CapabilityDefinition> capabilities = agent.capabilities().stream()
                    .filter(capability -> liveNames.contains(capability.tool()))
                    .map(capability -> sanitizeReferences(capability, retainedCapabilityIds))
                    .toList();
            AgentDefinition updated = new AgentDefinition(
                    agent.id(), agent.name(), agent.enabled(), agent.executor(), agent.server(),
                    capabilities
            );
            if (AUTO_AGENT_ID.equals(agent.id())) {
                autoAgent = updated;
            } else {
                agents.add(updated);
            }
        }

        List<CapabilityDefinition> discovered = new ArrayList<>(
                autoAgent == null ? List.of() : autoAgent.capabilities()
        );
        Set<String> usedIds = new HashSet<>();
        for (AgentDefinition agent : agents) {
            agent.capabilities().forEach(capability -> usedIds.add(capability.id()));
        }
        discovered.forEach(capability -> usedIds.add(capability.id()));

        for (McpSchema.Tool tool : liveTools) {
            if (tool == null || !StringUtils.hasText(tool.name())
                    || configuredTools.contains(tool.name().trim())) {
                continue;
            }
            String toolName = tool.name().trim();
            String capabilityId = uniqueCapabilityId(toolName, usedIds);
            discovered.add(new CapabilityDefinition(
                    capabilityId, toolName, true, List.of(), resolverFor(toolName), 0,
                    30_000, false, List.of(), 1, 100, List.of()
            ));
            usedIds.add(capabilityId);
        }

        if (!discovered.isEmpty()) {
            agents.add(new AgentDefinition(
                    AUTO_AGENT_ID, "Auto Discovered MCP Agent", true, "mcp", "ai-mcp", discovered
            ));
        }
        return new AgentConfigurationDocument(current.maxParallelism(), agents);
    }

    private CapabilityDefinition sanitizeReferences(
            CapabilityDefinition capability,
            Set<String> retainedCapabilityIds
    ) {
        return new CapabilityDefinition(
                capability.id(), capability.tool(), capability.enabled(), capability.intents(),
                capability.argumentResolver(), capability.priority(), capability.timeoutMs(),
                capability.requiresApproval(),
                capability.dependencies().stream().filter(retainedCapabilityIds::contains).toList(),
                capability.maxAttempts(), capability.retryBackoffMs(),
                capability.fallbackCapabilityIds().stream()
                        .filter(retainedCapabilityIds::contains).toList()
        );
    }

    private String uniqueCapabilityId(String toolName, Set<String> usedIds) {
        String normalized = toolName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", ".")
                .replaceAll("^\\.+|\\.+$", "");
        String candidate = "auto." + (normalized.isBlank() ? "tool" : normalized);
        return usedIds.contains(candidate)
                ? candidate + "." + Integer.toUnsignedString(toolName.hashCode(), 36)
                : candidate;
    }

    private String resolverFor(String toolName) {
        return switch (toolName) {
            case "describe_database_foreign_keys", "describe_database_table_columns",
                    "describe_database_comments", "describe_database_indexes" -> "database-table";
            case "generate_mybatis_mapper" -> "mybatis-mapper";
            case "search_database_tables" -> "database-search";
            case "list_database_tables" -> "database-table-list";
            case "analyze_project_structure" -> "project-path";
            case "search_source_ontology" -> "source-ontology";
            case "scan_file", "scan_project" -> "scan-path";
            case "scan_source" -> "scan-source";
            case "ocr_image_file" -> "ocr-image-file";
            case "ocr_image_base64" -> "ocr-image-base64";
            case "ocr_document_base64" -> "ocr-document-base64";
            case "get_server_info" -> "server-info";
            default -> "none";
        };
    }
}