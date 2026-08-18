package com.hanwha.ai.mcp.config;

public record AgentConfigurationView(
        String version,
        String source,
        AgentConfigurationDocument configuration
) {
}