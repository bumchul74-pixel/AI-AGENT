package com.hanwha.ai.mcp.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import com.hanwha.ai.global.exception.BusinessException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class AiMcpGatewayServiceTest {
    @Test
    void showsActionableMessageWhenMcpServerIsUnavailable() {
        ObjectProvider<List<McpSyncClient>> clientsProvider = new ObjectProvider<>() {
            @Override
            public List<McpSyncClient> getIfAvailable(Supplier<List<McpSyncClient>> defaultSupplier) {
                return List.of();
            }
        };
        AiMcpGatewayService service = new AiMcpGatewayService(clientsProvider);

        assertThatThrownBy(service::serverInfo)
                .isInstanceOf(BusinessException.class)
                .hasMessage("MCP 서버에 연결할 수 없습니다. MCP 서버 실행 상태와 연결 설정을 확인하세요.");
    }

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

    @Test
    void initializesClientOnlyOnceForConcurrentRequests() {
        McpSyncClient client = mock(McpSyncClient.class);
        AtomicBoolean initialized = new AtomicBoolean(false);
        when(client.isInitialized()).thenAnswer(invocation -> initialized.get());
        when(client.initialize()).thenAnswer(invocation -> {
            Thread.sleep(100);
            initialized.set(true);
            return null;
        });
        when(client.getServerInfo())
                .thenReturn(new McpSchema.Implementation("ai-mcp-server", "0.0.1"));
        ObjectProvider<List<McpSyncClient>> clientsProvider = new ObjectProvider<>() {
            @Override
            public List<McpSyncClient> getIfAvailable(Supplier<List<McpSyncClient>> defaultSupplier) {
                return List.of(client);
            }
        };
        AiMcpGatewayService service = new AiMcpGatewayService(clientsProvider);

        CompletableFuture<McpSchema.Implementation> first =
                CompletableFuture.supplyAsync(service::serverInfo);
        CompletableFuture<McpSchema.Implementation> second =
                CompletableFuture.supplyAsync(service::serverInfo);

        assertThat(CompletableFuture.allOf(first, second)).succeedsWithin(
                java.time.Duration.ofSeconds(2));
        assertThat(first.join().name()).isEqualTo("ai-mcp-server");
        assertThat(second.join().name()).isEqualTo("ai-mcp-server");
        verify(client, times(1)).initialize();
    }
}
