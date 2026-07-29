package com.hanwha.ai.mcp.gateway;

import com.hanwha.ai.global.exception.BusinessException;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(prefix = "spring.ai.mcp.client", name = "enabled", havingValue = "true")
public class EasyOcrMcpGatewayService {
    private static final Logger log = LoggerFactory.getLogger(EasyOcrMcpGatewayService.class);
    private static final String SERVER_NAME = "easyocr";
    private static final String CONNECTION_ERROR_MESSAGE =
            "EasyOCR MCP 서버에 연결할 수 없습니다. 서버 실행 상태와 연결 설정을 확인하세요.";

    private final ObjectProvider<List<McpSyncClient>> clientsProvider;

    public EasyOcrMcpGatewayService(ObjectProvider<List<McpSyncClient>> clientsProvider) {
        this.clientsProvider = clientsProvider;
    }

    public McpSchema.CallToolResult callTool(String toolName, Map<String, Object> arguments) {
        if (!StringUtils.hasText(toolName)) {
            throw new BusinessException("EasyOCR MCP tool name is required.");
        }
        return client().callTool(new McpSchema.CallToolRequest(
                toolName, arguments == null ? Map.of() : arguments));
    }

    private McpSyncClient client() {
        return clientsProvider.getIfAvailable(List::of).stream()
                .filter(this::isEasyOcrClient)
                .findFirst()
                .orElseThrow(() -> new BusinessException(CONNECTION_ERROR_MESSAGE));
    }

    private boolean isEasyOcrClient(McpSyncClient client) {
        try {
            synchronized (client) {
                if (!client.isInitialized()) {
                    client.initialize();
                }
            }
            McpSchema.Implementation serverInfo = client.getServerInfo();
            return serverInfo != null && SERVER_NAME.equals(serverInfo.name());
        } catch (RuntimeException exception) {
            log.debug("Skipping unavailable MCP client while resolving EasyOCR.", exception);
            return false;
        }
    }
}
