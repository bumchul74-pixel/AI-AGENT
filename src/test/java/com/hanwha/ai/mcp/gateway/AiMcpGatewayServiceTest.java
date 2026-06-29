package com.hanwha.ai.mcp.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class AiMcpGatewayServiceTest {
    @Test
    void lazilyInitializesMcpClientWithRetry() {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.isInitialized()).thenReturn(false);
        when(client.initialize())
                .thenThrow(new RuntimeException("MCP server is still starting"))
                .thenReturn(null);
        when(client.getServerInfo())
                .thenReturn(new McpSchema.Implementation("ai-mcp-server", "0.0.1"));
        ObjectProvider<List<McpSyncClient>> clientsProvider = new ObjectProvider<>() {
            @Override
            public List<McpSyncClient> getIfAvailable(Supplier<List<McpSyncClient>> defaultSupplier) {
                return List.of(client);
            }
        };
        AiMcpGatewayService service = new AiMcpGatewayService(clientsProvider);

        McpSchema.Implementation serverInfo = service.serverInfo();

        assertThat(serverInfo.name()).isEqualTo("ai-mcp-server");
        verify(client, times(2)).initialize();
    }
}