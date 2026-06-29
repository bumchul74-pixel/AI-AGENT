package com.hanwha.ai.mcp.gateway;

import com.hanwha.ai.global.exception.BusinessException;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(prefix = "spring.ai.mcp.client", name = "enabled", havingValue = "true")
public class AiMcpGatewayService {
    private static final Logger log = LoggerFactory.getLogger(AiMcpGatewayService.class);

    private static final String AI_MCP_SERVER_NAME = "ai-mcp-server";
    private static final String SERVER_INFO_TOOL = "get_server_info";
    private static final String SERVER_INFO_RESOURCE = "server://info";
    private static final int INITIALIZE_MAX_ATTEMPTS = 3;
    private static final long INITIALIZE_RETRY_DELAY_MILLIS = 1_000L;

    private final ObjectProvider<List<McpSyncClient>> clientsProvider;

    public AiMcpGatewayService(ObjectProvider<List<McpSyncClient>> clientsProvider) {
        this.clientsProvider = clientsProvider;
    }

    public Object ping() {
        return client().ping();
    }

    public McpSchema.Implementation serverInfo() {
        return client().getServerInfo();
    }

    public McpSchema.ListToolsResult listTools() {
        return client().listTools();
    }

    public McpSchema.CallToolResult callTool(String toolName, Map<String, Object> arguments) {
        if (!StringUtils.hasText(toolName)) {
            throw new BusinessException("MCP tool name is required.");
        }

        return client().callTool(new McpSchema.CallToolRequest(toolName, safeArguments(arguments)));
    }

    public McpSchema.CallToolResult getServerInfo(String detailLevel) {
        Map<String, Object> arguments = StringUtils.hasText(detailLevel)
                ? Map.of("arg0", detailLevel)
                : Map.of();

        return callTool(SERVER_INFO_TOOL, arguments);
    }

    public McpSchema.ListResourcesResult listResources() {
        return client().listResources();
    }

    public McpSchema.ReadResourceResult readResource(String uri) {
        if (!StringUtils.hasText(uri)) {
            throw new BusinessException("MCP resource uri is required.");
        }

        return client().readResource(new McpSchema.ReadResourceRequest(uri));
    }

    public McpSchema.ReadResourceResult readServerInfoResource() {
        return readResource(SERVER_INFO_RESOURCE);
    }

    public McpSchema.ListPromptsResult listPrompts() {
        return client().listPrompts();
    }

    public McpSchema.GetPromptResult getPrompt(String promptName, Map<String, Object> arguments) {
        if (!StringUtils.hasText(promptName)) {
            throw new BusinessException("MCP prompt name is required.");
        }

        return client().getPrompt(new McpSchema.GetPromptRequest(promptName, safeArguments(arguments)));
    }

    private McpSyncClient client() {
        return clientsProvider.getIfAvailable(List::of).stream()
                .filter(this::isAiMcpClient)
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "AI-MCP server is not connected. Check spring.ai.mcp.client.streamable-http.connections.ai-mcp."
                ));
    }

    private boolean isAiMcpClient(McpSyncClient client) {
        try {
            initializeWithRetry(client);

            McpSchema.Implementation serverInfo = client.getServerInfo();
            return serverInfo != null && AI_MCP_SERVER_NAME.equals(serverInfo.name());
        } catch (RuntimeException exception) {
            log.debug("Skipping unavailable MCP client while resolving AI-MCP.", exception);
            return false;
        }
    }

    private void initializeWithRetry(McpSyncClient client) {
        if (client.isInitialized()) {
            return;
        }

        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= INITIALIZE_MAX_ATTEMPTS; attempt++) {
            try {
                client.initialize();
                return;
            } catch (RuntimeException exception) {
                lastFailure = exception;
                log.warn(
                        "MCP client initialization failed. attempt={}/{}",
                        attempt,
                        INITIALIZE_MAX_ATTEMPTS,
                        exception
                );

                if (attempt < INITIALIZE_MAX_ATTEMPTS) {
                    sleepBeforeRetry();
                }
            }
        }

        throw lastFailure;
    }

    private void sleepBeforeRetry() {
        try {
            TimeUnit.MILLISECONDS.sleep(INITIALIZE_RETRY_DELAY_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying MCP client initialization.", exception);
        }
    }

    private Map<String, Object> safeArguments(Map<String, Object> arguments) {
        return arguments == null ? Map.of() : arguments;
    }
}
