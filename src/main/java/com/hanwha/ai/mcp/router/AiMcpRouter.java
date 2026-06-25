package com.hanwha.ai.mcp.router;

import org.springframework.stereotype.Component;

@Component
public class AiMcpRouter implements McpRouter {
    public static final String SERVER_NAME = "ai-mcp";

    @Override
    public String serverName() {
        return SERVER_NAME;
    }
}
