package com.hanwha.ai.sourcegraph.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanwha.ai.sourcegraph.dto.JavaSourceGraphIngestRequest;
import com.hanwha.ai.sourcegraph.dto.SourceGraphResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class StructuredSourceGraphAnalyzerTest {
    private final StructuredSourceGraphAnalyzer analyzer = new StructuredSourceGraphAnalyzer();

    @Test
    void mapsMyBatisXmlNamespaceStatementsTablesAndColumns() {
        SourceGraphResponse graph = analyzer.analyze(request(
                "UserMapper.xml",
                "src/main/resources/mapper/UserMapper.xml",
                """
                        <?xml version="1.0" encoding="UTF-8" ?>
                        <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
                          "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
                        <mapper namespace="com.example.UserMapper">
                          <select id="findById">
                            SELECT id, name FROM app.users WHERE id = #{id}
                          </select>
                          <update id="updateName">
                            UPDATE app.users SET name = #{name} WHERE id = #{id}
                          </update>
                        </mapper>
                        """
        ));

        assertThat(graph.nodes()).anyMatch(node ->
                node.id().equals("type:commerce:com.example.UserMapper")
                        && "Mapper".equals(node.properties().get("layer")));
        assertThat(graph.nodes()).anyMatch(node ->
                node.id().equals("statement:commerce:com.example.UserMapper:findById"));
        assertThat(graph.nodes()).anyMatch(node -> node.id().equals("table:commerce:app:users"));
        assertThat(graph.nodes()).anyMatch(node -> node.id().equals("column:commerce:app:users:id"));
        assertThat(graph.relationships()).extracting(relationship -> relationship.type())
                .contains("HAS_MAPPER_XML", "HAS_STATEMENT", "READS_FROM", "WRITES_TO", "REFERENCES_COLUMN");
    }

    @Test
    void mapsYamlPropertiesProfilesProvidersAndBeansWithoutSecrets() {
        SourceGraphResponse graph = analyzer.analyze(request(
                "application.yml",
                "src/main/resources/application.yml",
                """
                        spring:
                          profiles:
                            active: local
                          beans:
                            paymentClient:
                              class: com.example.PaymentClient
                        llm:
                          provider: openai
                        openai:
                          api-key: should-not-be-stored
                        """
        ));

        assertThat(graph.nodes()).extracting(node -> node.label())
                .contains("ConfigurationFile", "ConfigurationProperty", "Profile", "Provider", "Bean");
        assertThat(graph.nodes()).anyMatch(node -> node.id().equals("profile:commerce:local"));
        assertThat(graph.nodes()).anyMatch(node -> node.id().equals("provider:commerce:openai"));
        assertThat(graph.nodes()).anyMatch(node -> node.id().equals("bean:commerce:paymentClient"));
        assertThat(graph.nodes()).noneMatch(node -> node.properties().containsValue("should-not-be-stored"));
        assertThat(graph.nodes()).anyMatch(node -> node.properties().containsValue("[REDACTED]"));
    }

    @Test
    void structuresStandardRulesAndTemplatesFromMarkdown() {
        SourceGraphResponse graph = analyzer.analyze(request(
                "standards.md",
                "standards/standards.md",
                """
                        # Architecture Rules
                        - Controller must not call Repository directly.
                        # Standard Templates
                        - Service template must use constructor injection.
                        """
        ));

        assertThat(graph.nodes()).extracting(node -> node.label())
                .contains("StandardRule", "StandardTemplate");
        assertThat(graph.nodes()).filteredOn(node -> node.label().equals("StandardRule"))
                .allSatisfy(node -> assertThat(node.properties()).containsEntry("ruleType", "ARCHITECTURE"));
        assertThat(graph.relationships()).extracting(relationship -> relationship.type()).contains("DEFINES");
    }

    private JavaSourceGraphIngestRequest request(String fileName, String filePath, String content) {
        return new JavaSourceGraphIngestRequest(
                "document:100", fileName, content, "commerce", "backend", filePath, "hash100",
                List.of("document:100:chunk:0")
        );
    }
}
