package com.hanwha.ai.generation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanwha.ai.generation.dto.GenerationRequest;
import com.hanwha.ai.generation.repository.GenerationRepository;
import com.hanwha.ai.llm.config.LlmProperties;
import com.hanwha.ai.llm.domain.LlmProvider;
import com.hanwha.ai.llm.dto.LlmGenerateRequest;
import com.hanwha.ai.llm.dto.LlmGenerateResponse;
import com.hanwha.ai.llm.service.LlmClient;
import com.hanwha.ai.llm.service.LlmClientFactory;
import com.hanwha.ai.rag.config.RagProperties;
import com.hanwha.ai.rag.dto.RagSearchResponse;
import com.hanwha.ai.rag.service.RagClient;
import com.hanwha.ai.sourcegraph.service.NoOpSourceGraphService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GenerationServiceImplDatabaseSchemaContextTest {
    private static final String PROJECT_PATH = "D:\\workspace\\management";

    @Test
    void generateAddsMcpDatabaseSchemaContextBeforeRagForMapperDtoAndDomainTargets() {
        AtomicReference<String> ragQuery = new AtomicReference<>();
        RagClient ragClient = request -> {
            ragQuery.set(request.query());
            return new RagSearchResponse(List.of("standard mapper dto domain pattern"));
        };
        AtomicReference<LlmGenerateRequest> llmRequest = new AtomicReference<>();
        LlmClientFactory llmClientFactory = new LlmClientFactory(
                new LlmProperties("openai"),
                List.of(fakeLlmClient(llmRequest))
        );
        DatabaseSchemaContextProvider databaseSchemaContextProvider = (request, targetTypes) -> usersSchemaContext();
        GenerationService service = new GenerationServiceImpl(
                ragClient,
                llmClientFactory,
                new GenerationRepository(),
                new RagProperties("http://localhost:8000", "/api/search", 5),
                fakeProjectStructureAnalyzer(),
                NoOpSourceGraphService.INSTANCE,
                databaseSchemaContextProvider
        );

        var response = service.generate(new GenerationRequest(
                List.of("Mapper", "DTO", "DOMAIN"),
                "Create user CRUD artifacts",
                PROJECT_PATH
        ));

        assertThat(response.mcpContextApplied()).isTrue();
        assertThat(response.mcpContextMessage()).contains("MCP", "users", "analyze_project_structure", "generate_mybatis_mapper", "describe_database_table_columns");
        assertThat(ragQuery.get()).contains(
                "Mapper, DTO, DOMAIN",
                "MCP database schema context",
                "Matched database tables: users",
                "generate_mybatis_mapper"
        );
        assertThat(llmRequest.get().context()).contains(
                "MCP database schema context",
                "Matched database tables: users",
                "Retrieved RAG source",
                "standard mapper dto domain pattern"
        );
        assertThat(llmRequest.get().prompt()).contains(
                "use that DB context before RAG",
                "generate_mybatis_mapper output is present",
                "no matching DB table was found",
                "<select id=\"findList\">SELECT id, user_name FROM users</select>"
        );
    }

    @Test
    void generateExplainsWhenDatabaseMcpToolsRunButNoTableMatched() {
        RagClient ragClient = request -> new RagSearchResponse(List.of("standard mapper pattern"));
        AtomicReference<LlmGenerateRequest> llmRequest = new AtomicReference<>();
        LlmClientFactory llmClientFactory = new LlmClientFactory(
                new LlmProperties("openai"),
                List.of(fakeLlmClient(llmRequest))
        );
        GenerationService service = new GenerationServiceImpl(
                ragClient,
                llmClientFactory,
                new GenerationRepository(),
                new RagProperties("http://localhost:8000", "/api/search", 5),
                fakeProjectStructureAnalyzer(),
                NoOpSourceGraphService.INSTANCE,
                (request, targetTypes) -> noMatchedTableSchemaContext()
        );

        var response = service.generate(new GenerationRequest(
                List.of("Mapper"),
                "Create rag_document Mapper XML",
                PROJECT_PATH
        ));

        assertThat(response.mcpContextApplied()).isTrue();
        assertThat(response.mcpContextMessage()).contains(
                "analyze_project_structure",
                "list_database_tables",
                "search_database_tables",
                "No matching database table"
        );
        assertThat(llmRequest.get().context()).contains("RAG fallback");
    }
    @Test
    void regenerateOnceWhenMapperXmlUsesColumnMissingFromDatabaseMetadata() {
        RagClient ragClient = request -> new RagSearchResponse(List.of("standard mapper dto domain pattern"));
        List<LlmGenerateRequest> llmRequests = new ArrayList<>();
        LlmClientFactory llmClientFactory = new LlmClientFactory(
                new LlmProperties("openai"),
                List.of(sequenceLlmClient(
                        llmRequests,
                        """
                                <mapper namespace=\"UserMapper\">
                                    <resultMap id=\"userResultMap\" type=\"User\">
                                        <id column=\"id\" property=\"id\" />
                                        <result column=\"fake_column\" property=\"fakeColumn\" />
                                    </resultMap>
                                    <select id=\"findList\" resultMap=\"userResultMap\">
                                        SELECT id, fake_column FROM users
                                    </select>
                                </mapper>
                                """,
                        """
                                <mapper namespace=\"UserMapper\">
                                    <resultMap id=\"userResultMap\" type=\"User\">
                                        <id column=\"id\" property=\"id\" />
                                        <result column=\"user_name\" property=\"userName\" />
                                    </resultMap>
                                    <select id=\"findList\" resultMap=\"userResultMap\">
                                        SELECT id, user_name FROM users
                                    </select>
                                </mapper>
                                """
                ))
        );
        GenerationService service = new GenerationServiceImpl(
                ragClient,
                llmClientFactory,
                new GenerationRepository(),
                new RagProperties("http://localhost:8000", "/api/search", 5),
                fakeProjectStructureAnalyzer(),
                NoOpSourceGraphService.INSTANCE,
                (request, targetTypes) -> usersSchemaContext()
        );

        var response = service.generate(new GenerationRequest(
                List.of("Mapper"),
                "Create users Mapper XML",
                PROJECT_PATH
        ));

        assertThat(response.generatedCode()).contains("user_name").doesNotContain("fake_column");
        assertThat(llmRequests).hasSize(2);
        assertThat(llmRequests.get(1).prompt()).contains(
                "Previous Mapper XML validation failed",
                "Invalid columns: fake_column",
                "using only the allowed columns"
        );
    }

    private DatabaseSchemaContext usersSchemaContext() {
        return new DatabaseSchemaContext(
                true,
                """
                        Source priority: MCP connected database table metadata. Use this before RAG for Mapper, DTO, and DOMAIN content.
                        Matched database tables: users
                        Allowed columns for table users: id, user_name
                        Tool: describe_database_table_columns
                        Result:
                        id BIGINT PK
                        user_name VARCHAR(100)
                        Tool: generate_mybatis_mapper
                        Result:
                        <select id=\"findList\">SELECT id, user_name FROM users</select>
                        """,
                List.of("users"),
                Map.of("users", List.of("id", "user_name")),
                List.of(
                        "list_database_tables",
                        "search_database_tables",
                        "describe_database_table_columns",
                        "describe_database_foreign_keys",
                        "describe_database_indexes",
                        "describe_database_comments",
                        "generate_mybatis_mapper"
                )
        );
    }

    private DatabaseSchemaContext noMatchedTableSchemaContext() {
        return new DatabaseSchemaContext(
                false,
                """
                        Source priority: MCP connected database table metadata. Use this before RAG for Mapper, DTO, and DOMAIN content.
                        Tool: list_database_tables
                        Result:
                        ["users", "orders"]
                        Tool: search_database_tables
                        Result:
                        {"tables":[]}
                        Table selection: no matching database table was found. RAG fallback must be used for table and field information.
                        """,
                List.of(),
                Map.of(),
                List.of("list_database_tables", "search_database_tables"),
                List.of("list_database_tables", "search_database_tables"),
                "No matching database table was found by MCP DB tools."
        );
    }
    private ProjectStructureAnalyzer fakeProjectStructureAnalyzer() {
        return (projectPath, targetTypes) -> """
                MCP project structure analysis:
                Project full path: %s
                Selected target types: %s
                Base package: com.hanwha.ai
                """.formatted(projectPath, String.join(", ", targetTypes));
    }

    private LlmClient fakeLlmClient(AtomicReference<LlmGenerateRequest> llmRequest) {
        return new LlmClient() {
            @Override
            public LlmProvider provider() {
                return LlmProvider.OPENAI;
            }

            @Override
            public LlmGenerateResponse generate(LlmGenerateRequest request) {
                llmRequest.set(request);
                return new LlmGenerateResponse(request.context());
            }
        };
    }

    private LlmClient sequenceLlmClient(List<LlmGenerateRequest> llmRequests, String... responses) {
        return new LlmClient() {
            private int index;

            @Override
            public LlmProvider provider() {
                return LlmProvider.OPENAI;
            }

            @Override
            public LlmGenerateResponse generate(LlmGenerateRequest request) {
                llmRequests.add(request);
                String response = responses[Math.min(index, responses.length - 1)];
                index++;
                return new LlmGenerateResponse(response);
            }
        };
    }
}