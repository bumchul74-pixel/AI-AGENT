package com.hanwha.ai.mcp.dto;

import java.util.List;

public record AgentToolCatalogResponse(
        String configurationVersion,
        List<ToolItem> tools
) {
    public AgentToolCatalogResponse {
        tools = tools == null ? List.of() : List.copyOf(tools);
    }

    public record ToolItem(
            String name,
            String description,
            String agentId,
            String capabilityId,
            String server,
            InputSchema inputSchema
    ) {
    }

    public record InputSchema(List<String> required) {
        public InputSchema {
            required = required == null ? List.of() : List.copyOf(required);
        }
    }
}