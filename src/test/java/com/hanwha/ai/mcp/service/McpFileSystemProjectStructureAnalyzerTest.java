package com.hanwha.ai.mcp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hanwha.ai.global.exception.BusinessException;
import com.hanwha.ai.mcp.gateway.AiMcpGatewayService;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class McpFileSystemProjectStructureAnalyzerTest {
    @Test
    void callsAnalyzeProjectStructureToolWithProjectPathArgument() {
        AtomicReference<String> toolName = new AtomicReference<>();
        AtomicReference<Map<String, Object>> arguments = new AtomicReference<>();
        McpFileSystemProjectStructureAnalyzer analyzer = new McpFileSystemProjectStructureAnalyzer(
                new CapturingAiMcpGatewayService(toolName, arguments)
        );

        String analysis = analyzer.analyze("D:\\workspace\\management", List.of("Controller"));

        assertThat(toolName.get()).isEqualTo("analyze_project_structure");
        assertThat(arguments.get()).containsEntry("project_path", "D:\\workspace\\management");
        assertThat(analysis)
                .contains("Tool: analyze_project_structure")
                .contains("Spring Boot: detected");
    }

    @Test
    void failsWithoutLocalFallbackWhenMcpToolIsUnavailable() {
        McpFileSystemProjectStructureAnalyzer analyzer = new McpFileSystemProjectStructureAnalyzer(
                new FailingAiMcpGatewayService()
        );

        assertThatThrownBy(() -> analyzer.analyze("D:\\workspace\\management", List.of("Controller")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("MCP project structure analysis failed.")
                .hasRootCauseMessage("Unknown tool: invalid_tool_name");
    }

    private static class CapturingAiMcpGatewayService extends AiMcpGatewayService {
        private final AtomicReference<String> toolName;
        private final AtomicReference<Map<String, Object>> arguments;

        private CapturingAiMcpGatewayService(
                AtomicReference<String> toolName,
                AtomicReference<Map<String, Object>> arguments
        ) {
            super(null);
            this.toolName = toolName;
            this.arguments = arguments;
        }

        @Override
        public McpSchema.CallToolResult callTool(String toolName, Map<String, Object> arguments) {
            this.toolName.set(toolName);
            this.arguments.set(arguments);
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent("Spring Boot: detected")),
                    false,
                    null,
                    Map.of()
            );
        }
    }

    private static class FailingAiMcpGatewayService extends AiMcpGatewayService {
        private FailingAiMcpGatewayService() {
            super(null);
        }

        @Override
        public McpSchema.CallToolResult callTool(String toolName, Map<String, Object> arguments) {
            throw new RuntimeException("Unknown tool: invalid_tool_name");
        }
    }
}