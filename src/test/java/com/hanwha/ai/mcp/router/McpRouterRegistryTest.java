package com.hanwha.ai.mcp.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class McpRouterRegistryTest {
    @Test
    void findsRouterByMcpServerName() {
        McpRouter serverFilesystemRouter = () -> "server-filesystem";
        McpRouterRegistry registry = new McpRouterRegistry(List.of(serverFilesystemRouter));

        assertThat(registry.getRouter("server-filesystem"))
                .isSameAs(serverFilesystemRouter);
    }

    @Test
    void throwsExceptionWhenMcpServerIsNotSupported() {
        McpRouterRegistry registry = new McpRouterRegistry(List.of());

        assertThatThrownBy(() -> registry.getRouter("unknown-server"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported MCP server: unknown-server");
    }
}
