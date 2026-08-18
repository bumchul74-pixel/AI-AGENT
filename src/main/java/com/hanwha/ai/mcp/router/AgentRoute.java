package com.hanwha.ai.mcp.router;

import java.util.Map;

public record AgentRoute(Kind kind, String operation, String target, Map<String, Object> arguments) {
    public AgentRoute {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }

    public enum Kind {
        TOOL_CALL,
        PROJECT_STRUCTURE_ANALYSIS,
        RESOURCE_READ,
        RESOURCE_LIST,
        PROMPT_GET,
        PROMPT_LIST,
        TOOL_LIST,
        PING,
        SERVER_INFO
    }
}