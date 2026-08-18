package com.hanwha.ai.mcp.service;

import com.hanwha.ai.mcp.dto.AgentToolCatalogResponse;
import com.hanwha.ai.mcp.router.AgentCapability;
import com.hanwha.ai.mcp.router.AgentRegistry;
import com.hanwha.ai.mcp.router.AgentRegistrySnapshot;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AgentToolCatalogService {
    private final AgentRegistry agentRegistry;

    public AgentToolCatalogService(AgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
    }

    public AgentToolCatalogResponse activeTools() {
        AgentRegistrySnapshot snapshot = agentRegistry.snapshot();
        return new AgentToolCatalogResponse(
                snapshot.version(),
                snapshot.capabilities().stream().map(this::toolItem).toList()
        );
    }

    private AgentToolCatalogResponse.ToolItem toolItem(AgentCapability capability) {
        return new AgentToolCatalogResponse.ToolItem(
                capability.tool(),
                capability.id() + " · " + capability.agentId(),
                capability.agentId(),
                capability.id(),
                capability.server(),
                new AgentToolCatalogResponse.InputSchema(
                        requiredArguments(capability.argumentResolver())
                )
        );
    }

    private List<String> requiredArguments(String resolverName) {
        return switch (resolverName) {
            case "database-table", "mybatis-mapper" -> List.of("tableName");
            case "database-search" -> List.of("keyword");
            case "project-path" -> List.of("projectPath");
            case "scan-path" -> List.of("path");
            case "scan-source" -> List.of("fileName", "source");
            case "ocr-image-file" -> List.of("file_path");
            case "ocr-image-base64" -> List.of("image_base64");
            case "ocr-document-base64" -> List.of("file_base64", "file_name");
            default -> List.of();
        };
    }
}