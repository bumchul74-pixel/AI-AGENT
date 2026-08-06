package com.hanwha.ai.mcp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hanwha.ai.generation.service.ProjectStructureAnalyzer;
import com.hanwha.ai.mcp.gateway.AiMcpGatewayService;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AiMcpChatContextProviderTest {
    @Test
    void routesEveryExplicitToolNameToToolsCall() {
        List<String> toolNames = new java.util.ArrayList<>();
        List<Map<String, Object>> capturedArguments = new java.util.ArrayList<>();
        AiMcpGatewayService gateway = new AiMcpGatewayService(null) {
            @Override
            public McpSchema.CallToolResult callTool(String name, Map<String, Object> toolArguments) {
                toolNames.add(name);
                capturedArguments.add(toolArguments);
                return new McpSchema.CallToolResult(List.of(), false, null, Map.of());
            }
        };
        AiMcpChatContextProvider provider = new AiMcpChatContextProvider(
                gateway,
                (projectPath, targetTypes) -> ""
        );
        List<String> messages = List.of(
                "ocr_document_base64 file_base64=YWJj file_name=test.pdf",
                "describe_database_foreign_keys tableName=users",
                "describe_database_table_columns users 테이블",
                "generate_mybatis_mapper tableName=users operations=SELECT",
                "describe_database_comments tableName=users",
                "describe_database_indexes tableName=users",
                "search_source_ontology query=AuthController",
                "search_database_tables keyword=user",
                "analyze_project_structure projectPath=D:\\workspace\\demo",
                "list_database_tables schemaName=public",
                "ocr_image_base64 image_base64=YWJj",
                "ocr_image_file file_path=D:\\images\\sample.png",
                "scan_project path=demo",
                "scan_source fileName=Test.java source=\"class Test {}\"",
                "scan_file path=src/main/Test.java",
                "get_server_info extended",
                "list_rules"
        );

        messages.forEach(provider::resolveContext);

        assertThat(toolNames).containsExactly(
                "ocr_document_base64",
                "describe_database_foreign_keys",
                "describe_database_table_columns",
                "generate_mybatis_mapper",
                "describe_database_comments",
                "describe_database_indexes",
                "search_source_ontology",
                "search_database_tables",
                "analyze_project_structure",
                "list_database_tables",
                "ocr_image_base64",
                "ocr_image_file",
                "scan_project",
                "scan_source",
                "scan_file",
                "get_server_info",
                "list_rules"
        );
        assertThat(capturedArguments.get(2)).containsEntry("tableName", "users");
        assertThat(capturedArguments.get(8)).containsEntry("projectPath", "D:\\workspace\\demo");
        assertThat(capturedArguments.get(13))
                .containsEntry("fileName", "Test.java")
                .containsEntry("source", "class Test {}");
        assertThat(capturedArguments.get(15)).containsEntry("detailLevel", "EXTENDED");
    }

    @Test
    void rejectsExplicitToolCallWhenRequiredArgumentIsMissing() {
        List<String> toolNames = new java.util.ArrayList<>();
        AiMcpGatewayService gateway = new AiMcpGatewayService(null) {
            @Override
            public McpSchema.CallToolResult callTool(String name, Map<String, Object> toolArguments) {
                toolNames.add(name);
                return new McpSchema.CallToolResult(List.of(), false, null, Map.of());
            }
        };
        AiMcpChatContextProvider provider = new AiMcpChatContextProvider(
                gateway,
                (projectPath, targetTypes) -> ""
        );

        assertThatThrownBy(() -> provider.resolveContext("scan_file tool을 실행해줘"))
                .isInstanceOf(com.hanwha.ai.global.exception.BusinessException.class)
                .hasMessageContaining("scan_file", "path");
        assertThat(toolNames).isEmpty();
    }

    @Test
    void routesNaturalLanguageRuleListRequestToListRules() {
        AtomicReference<String> toolName = new AtomicReference<>();
        AiMcpGatewayService gateway = new AiMcpGatewayService(null) {
            @Override
            public McpSchema.CallToolResult callTool(String name, Map<String, Object> toolArguments) {
                toolName.set(name);
                return new McpSchema.CallToolResult(List.of(), false, null, Map.of());
            }
        };
        AiMcpChatContextProvider provider = new AiMcpChatContextProvider(
                gateway,
                (projectPath, targetTypes) -> ""
        );

        provider.resolveContext("MCP 보안 규칙 목록을 보여줘");

        assertThat(toolName.get()).isEqualTo("list_rules");
    }

    @Test
    void routesDatabaseTableListRequestToNamedTool() {
        AtomicReference<String> toolName = new AtomicReference<>();
        AtomicReference<Map<String, Object>> arguments = new AtomicReference<>();
        AiMcpGatewayService gateway = new AiMcpGatewayService(null) {
            @Override
            public McpSchema.CallToolResult callTool(String name, Map<String, Object> toolArguments) {
                toolName.set(name);
                arguments.set(toolArguments);
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("[\"users\", \"orders\"]")),
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

        List<String> contexts = provider.resolveContext(
                "MCP의 tool 중에 list_database_tables를 활용해서 전체 테이블 목록을 보여줘"
        );

        assertThat(toolName.get()).isEqualTo("list_database_tables");
        assertThat(arguments.get()).isEmpty();
        assertThat(contexts).singleElement().asString().contains(
                "tools/call list_database_tables",
                "users",
                "orders"
        );
    }

    @Test
    void routesKoreanDatabaseTableListIntentToNamedTool() {
        AtomicReference<String> toolName = new AtomicReference<>();
        AiMcpGatewayService gateway = new AiMcpGatewayService(null) {
            @Override
            public McpSchema.CallToolResult callTool(String name, Map<String, Object> toolArguments) {
                toolName.set(name);
                return new McpSchema.CallToolResult(List.of(), false, null, Map.of());
            }
        };
        AiMcpChatContextProvider provider = new AiMcpChatContextProvider(
                gateway,
                (projectPath, targetTypes) -> ""
        );

        provider.resolveContext("MCP를 활용해서 테이블 목록을 보여줘");

        assertThat(toolName.get()).isEqualTo("list_database_tables");
    }

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
