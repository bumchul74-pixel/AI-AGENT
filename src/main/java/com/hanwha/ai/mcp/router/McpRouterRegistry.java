package com.hanwha.ai.mcp.router;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class McpRouterRegistry {
    private final List<McpRouter> routers;

    public McpRouterRegistry(List<McpRouter> routers) {
        this.routers = routers;
    }

    public McpRouter getRouter(String serverName) {
        return routers.stream()
                .filter(router -> router.serverName().equals(serverName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported MCP server: " + serverName));
    }
}
