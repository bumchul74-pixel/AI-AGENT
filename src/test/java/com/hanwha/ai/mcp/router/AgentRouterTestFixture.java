package com.hanwha.ai.mcp.router;

import java.time.Duration;
import java.util.List;
import java.util.Set;

public final class AgentRouterTestFixture {
    private AgentRouterTestFixture() {
    }

    public static AgentRouter router() {
        return new AgentRouter(registry(), List.of(new DefaultAgentArgumentResolver()));
    }

    public static AgentRegistry registry() {
        return AgentRegistry.of(List.of(
                capability("ocr.document", "ocr_document_base64", "ocr-document-base64", 170),
                capability("database.foreign-keys", "describe_database_foreign_keys", "database-table", 160),
                capability("database.columns", "describe_database_table_columns", "database-table", 150),
                capability("database.mybatis-mapper", "generate_mybatis_mapper", "mybatis-mapper", 140),
                capability("database.comments", "describe_database_comments", "database-table", 130),
                capability("database.indexes", "describe_database_indexes", "database-table", 120),
                capability("source.search", "search_source_ontology", "source-ontology", 110),
                capability("database.search", "search_database_tables", "database-search", 100),
                capability("project.structure", "analyze_project_structure", "project-path", 90),
                capability("database.table-list", "list_database_tables", "database-table-list", 80),
                capability("ocr.image-base64", "ocr_image_base64", "ocr-image-base64", 70),
                capability("ocr.image-file", "ocr_image_file", "ocr-image-file", 60),
                capability("security.project-scan", "scan_project", "scan-path", 50),
                capability("security.source-scan", "scan_source", "scan-source", 40),
                capability("security.file-scan", "scan_file", "scan-path", 30),
                capability("server.info", "get_server_info", "server-info", 20),
                capability("security.rule-list", "list_rules", "none", 10)
        ));
    }

    private static AgentCapability capability(String id, String tool, String resolver, int priority) {
        return new AgentCapability(
                "test-agent",
                id,
                "mcp",
                "ai-mcp",
                tool,
                Set.of(id),
                resolver,
                priority,
                Duration.ofSeconds(30),
                false
        );
    }
}