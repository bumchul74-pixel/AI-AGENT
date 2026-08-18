package com.hanwha.ai.mcp.router;

import java.util.Map;

public interface AgentArgumentResolver {
    boolean supports(String resolverName);

    Map<String, Object> resolve(AgentCapability capability, String message, String normalizedMessage);
}