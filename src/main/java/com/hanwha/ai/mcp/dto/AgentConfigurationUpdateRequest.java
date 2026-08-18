package com.hanwha.ai.mcp.dto;

import com.hanwha.ai.mcp.config.AgentConfigurationDocument;

public record AgentConfigurationUpdateRequest(
        AgentConfigurationDocument configuration
) {
}
