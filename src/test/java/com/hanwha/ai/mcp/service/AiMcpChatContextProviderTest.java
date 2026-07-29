package com.hanwha.ai.mcp.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanwha.ai.generation.service.ProjectStructureAnalyzer;
import com.hanwha.ai.mcp.gateway.AiMcpGatewayService;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AiMcpChatContextProviderTest {
    @Test
    void routesJavaSourceQuestionToSourceOntologyTool() {
        AtomicReference<String> toolName = new AtomicReference<>();
        AtomicReference<Map<String, Object>> arguments = new AtomicReference<>();
        AiMcpGatewayService gateway = new AiMcpGatewayService(null) {
            @Override
            public McpSchema.CallToolResult callTool(String name, Map<String, Object> toolArguments) {
                toolName.set(name);
                arguments.set(toolArguments);
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("OrderService -> OrderRepository")),
                        false,
                        null,
                        Map.of()
                );
            }
        };
        AiMcpChatContextProvider provider = new AiMcpChatContextProvider(
                gateway,
                (projectPath, targetTypes) -> ""
        );
        String message = "AuthController와 관련된 Java소스들을 MCP에서 조회해줘";

        assertThat(provider.supports(message)).isTrue();
        List<String> contexts = provider.resolveContext(message);

        assertThat(toolName.get()).isEqualTo("search_source_ontology");
        assertThat(arguments.get()).containsExactlyEntriesOf(Map.of("query", "AuthController"));
        assertThat(arguments.get()).doesNotContainKey("projectId");
        assertThat(contexts).singleElement().asString().contains(
                "tools/call search_source_ontology",
                "query=AuthController",
                "OrderService -> OrderRepository"
        );
    }

    @Test
    void routesLocalProjectStructureRequestToProjectStructureAnalyzer() {
        AtomicReference<String> analyzedPath = new AtomicReference<>();
        ProjectStructureAnalyzer projectStructureAnalyzer = (projectPath, targetTypes) -> {
            analyzedPath.set(projectPath);
            return """
                    Local project structure analysis:
                    Project full path: %s
                    Spring Boot version: 3.2.0
                    Java version: 21
                    """.formatted(projectPath);
        };
        AiMcpChatContextProvider provider = new AiMcpChatContextProvider(
                new AiMcpGatewayService(null),
                projectStructureAnalyzer
        );
        String message = "\ub85c\uceec D:\\workspace\\management \uc758 \ud504\ub85c\uc81d\ud2b8 \uad6c\uc870\ub97c \ubd84\uc11d\ud574\uc918";

        assertThat(provider.supports(message)).isTrue();

        List<String> contexts = provider.resolveContext(message);

        assertThat(analyzedPath.get()).isEqualTo("D:\\workspace\\management");
        assertThat(contexts).hasSize(1);
        assertThat(contexts.get(0)).contains(
                "MCP gateway operation:",
                "project-structure/analyze D:\\workspace\\management",
                "Spring Boot version: 3.2.0",
                "Java version: 21"
        );
    }
}
