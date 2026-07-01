package com.hanwha.ai.generation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanwha.ai.generation.dto.GenerationRequest;
import com.hanwha.ai.mcp.gateway.AiMcpGatewayService;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpDatabaseSchemaContextProviderTest {
    @Test
    void resolvesDatabaseSchemaContextWithMcpDatabaseTools() {
        CapturingAiMcpGatewayService gatewayService = new CapturingAiMcpGatewayService();
        McpDatabaseSchemaContextProvider provider = new McpDatabaseSchemaContextProvider(gatewayService);

        DatabaseSchemaContext context = provider.resolve(
                new GenerationRequest(List.of("Mapper", "DTO", "DOMAIN"), "Create user CRUD", "D:\\workspace\\management"),
                List.of("Mapper", "DTO", "DOMAIN")
        );

        assertThat(context.matchedTables()).isTrue();
        assertThat(context.tableNames()).containsExactly("users");
        assertThat(context.tableColumns()).containsEntry("users", List.of("id", "user_name", "email"));
        assertThat(context.appliedTools()).containsExactly(
                "list_database_tables",
                "search_database_tables",
                "describe_database_table_columns",
                "describe_database_foreign_keys",
                "describe_database_indexes",
                "describe_database_comments",
                "generate_mybatis_mapper"
        );
        assertThat(context.content()).contains(
                "Matched database tables: users",
                "Allowed columns for table users: id, user_name, email",
                "Tool: list_database_tables",
                "Tool: search_database_tables",
                "Tool: describe_database_table_columns",
                "Tool: describe_database_foreign_keys",
                "Tool: describe_database_indexes",
                "Tool: describe_database_comments",
                "Tool: generate_mybatis_mapper",
                "<select id=\"findList\""
        );
        assertThat(gatewayService.toolNames()).containsExactly(
                "list_database_tables",
                "search_database_tables",
                "describe_database_table_columns",
                "describe_database_foreign_keys",
                "describe_database_indexes",
                "describe_database_comments",
                "generate_mybatis_mapper"
        );
        assertThat(gatewayService.arguments().get(1)).containsEntry("query", "Create user CRUD");
        assertThat(gatewayService.arguments().get(2)).containsEntry("table_name", "users");
    }

    @Test
    void locksExactPromptTableNameBeforeSearchResults() {
        ExactPromptTableAiMcpGatewayService gatewayService = new ExactPromptTableAiMcpGatewayService();
        McpDatabaseSchemaContextProvider provider = new McpDatabaseSchemaContextProvider(gatewayService);

        DatabaseSchemaContext context = provider.resolve(
                new GenerationRequest(List.of("Mapper"), "rag_document Mapper XML을 생성해줘", "D:\\workspace\\management"),
                List.of("Mapper")
        );

        assertThat(context.matchedTables()).isTrue();
        assertThat(context.tableNames()).containsExactly("rag_document");
        assertThat(context.tableColumns()).containsEntry("rag_document", List.of("id", "original_file_name", "stored_file_name"));
        assertThat(context.appliedTools()).containsExactly(
                "list_database_tables",
                "search_database_tables",
                "describe_database_table_columns",
                "describe_database_foreign_keys",
                "describe_database_indexes",
                "describe_database_comments",
                "generate_mybatis_mapper"
        );
        assertThat(context.content()).contains(
                "Matched database tables: rag_document",
                "exact table name from user prompt",
                "Allowed columns for table rag_document: id, original_file_name, stored_file_name"
        );
        assertThat(gatewayService.toolNames()).containsExactly(
                "list_database_tables",
                "search_database_tables",
                "describe_database_table_columns",
                "describe_database_foreign_keys",
                "describe_database_indexes",
                "describe_database_comments",
                "generate_mybatis_mapper"
        );
        assertThat(gatewayService.arguments().get(2)).containsEntry("table_name", "rag_document");
    }

    @Test
    void resolvesDatabaseSchemaContextForMapperXmlTargetAlias() {
        CapturingAiMcpGatewayService gatewayService = new CapturingAiMcpGatewayService();
        McpDatabaseSchemaContextProvider provider = new McpDatabaseSchemaContextProvider(gatewayService);

        DatabaseSchemaContext context = provider.resolve(
                new GenerationRequest(List.of("Mapper XML"), "Create user Mapper XML", "D:\\workspace\\management"),
                List.of("Mapper XML")
        );

        assertThat(context.matchedTables()).isTrue();
        assertThat(context.tableNames()).containsExactly("users");
        assertThat(context.appliedTools()).contains("generate_mybatis_mapper");
        assertThat(gatewayService.toolNames()).contains("list_database_tables", "search_database_tables", "generate_mybatis_mapper");
    }

    @Test
    void keepsDatabaseToolDiagnosticsWhenNoTableMatched() {
        NoMatchAiMcpGatewayService gatewayService = new NoMatchAiMcpGatewayService();
        McpDatabaseSchemaContextProvider provider = new McpDatabaseSchemaContextProvider(gatewayService);

        DatabaseSchemaContext context = provider.resolve(
                new GenerationRequest(List.of("Mapper"), "Create rag_document Mapper XML", "D:\\workspace\\management"),
                List.of("Mapper")
        );

        assertThat(context.matchedTables()).isFalse();
        assertThat(context.attemptedTools()).containsExactly("list_database_tables", "search_database_tables");
        assertThat(context.appliedTools()).containsExactly("list_database_tables", "search_database_tables");
        assertThat(context.statusMessage()).contains("No matching database table");
        assertThat(context.content()).contains("RAG fallback", "Tool: list_database_tables", "Tool: search_database_tables");
        assertThat(gatewayService.toolNames()).containsExactly("list_database_tables", "search_database_tables");
    }
    @Test
    void skipsMcpDatabaseToolsWhenSelectedTargetsDoNotNeedDatabaseSchema() {
        CapturingAiMcpGatewayService gatewayService = new CapturingAiMcpGatewayService();
        McpDatabaseSchemaContextProvider provider = new McpDatabaseSchemaContextProvider(gatewayService);

        DatabaseSchemaContext context = provider.resolve(
                new GenerationRequest(List.of("Controller"), "Create user controller", "D:\\workspace\\management"),
                List.of("Controller")
        );

        assertThat(context.hasContent()).isFalse();
        assertThat(gatewayService.toolNames()).isEmpty();
    }

    private static McpSchema.CallToolResult textResult(String text) {
        return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(text)),
                false,
                null,
                Map.of()
        );
    }

    private static class CapturingAiMcpGatewayService extends AiMcpGatewayService {
        private final List<String> toolNames = new ArrayList<>();
        private final List<Map<String, Object>> arguments = new ArrayList<>();

        private CapturingAiMcpGatewayService() {
            super(null);
        }

        @Override
        public McpSchema.CallToolResult callTool(String toolName, Map<String, Object> arguments) {
            this.toolNames.add(toolName);
            this.arguments.add(arguments);

            return switch (toolName) {
                case "list_database_tables" -> textResult("[\"users\", \"orders\"]");
                case "search_database_tables" -> textResult("{\"tables\":[{\"table_name\":\"users\"}]}");
                case "describe_database_table_columns" -> textResult("id BIGINT PK\nuser_name VARCHAR(100) NOT NULL\nemail VARCHAR(200)");
                case "describe_database_foreign_keys" -> textResult("No foreign keys");
                case "describe_database_indexes" -> textResult("pk_users(id)\nuk_users_email(email)");
                case "describe_database_comments" -> textResult("users: user master table\nuser_name: user name");
                case "generate_mybatis_mapper" -> textResult("<select id=\"findList\" resultType=\"User\">SELECT id, user_name, email FROM users</select>");
                default -> throw new RuntimeException("Unexpected tool: " + toolName);
            };
        }

        protected List<String> toolNames() {
            return toolNames;
        }

        protected List<Map<String, Object>> arguments() {
            return arguments;
        }
    }

    private static class NoMatchAiMcpGatewayService extends CapturingAiMcpGatewayService {
        @Override
        public McpSchema.CallToolResult callTool(String toolName, Map<String, Object> arguments) {
            toolNames().add(toolName);
            arguments().add(arguments);

            return switch (toolName) {
                case "list_database_tables" -> textResult("[\"users\", \"orders\"]");
                case "search_database_tables" -> textResult("{\"tables\":[]}");
                default -> throw new RuntimeException("Unexpected tool: " + toolName);
            };
        }
    }
    private static class ExactPromptTableAiMcpGatewayService extends CapturingAiMcpGatewayService {
        @Override
        public McpSchema.CallToolResult callTool(String toolName, Map<String, Object> arguments) {
            toolNames().add(toolName);
            arguments().add(arguments);

            return switch (toolName) {
                case "list_database_tables" -> textResult("[\"rag_document\", \"generation_history\"]");
                case "search_database_tables" -> textResult("{\"tables\":[{\"table_name\":\"generation_history\"}]}");
                case "describe_database_table_columns" -> textResult("id BIGINT PK\noriginal_file_name VARCHAR(255)\nstored_file_name VARCHAR(255)");
                case "describe_database_foreign_keys" -> textResult("No foreign keys");
                case "describe_database_indexes" -> textResult("idx_rag_document_status(index_status)");
                case "describe_database_comments" -> textResult("rag_document: uploaded RAG documents");
                case "generate_mybatis_mapper" -> textResult("<select id=\"findList\">SELECT id, original_file_name, stored_file_name FROM rag_document</select>");
                default -> throw new RuntimeException("Unexpected tool: " + toolName);
            };
        }
    }
}