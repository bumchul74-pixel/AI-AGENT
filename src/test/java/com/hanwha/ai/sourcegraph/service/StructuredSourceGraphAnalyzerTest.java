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
    void resolvesIncludesAndDynamicSqlIntoVariantsAndStatementEvidence() {
        String xml = """
                <mapper namespace="com.example.UserMapper">
                  <sql id="columns">${alias}.id, ${alias}.name</sql>
                  <select id="search" parameterType="map" resultType="User">
                    SELECT <include refid="columns"><property name="alias" value="u"/></include>
                    FROM app.users u
                    <where>
                      <if test="name != null">AND u.name = #{name}</if>
                      <choose>
                        <when test="active != null">AND u.active = #{active}</when>
                        <otherwise>AND u.deleted = false</otherwise>
                      </choose>
                      <if test="ids != null">
                        AND u.id IN
                        <foreach collection="ids" item="id" open="(" close=")" separator=",">#{id}</foreach>
                      </if>
                    </where>
                  </select>
                </mapper>
                """;
        JavaSourceGraphIngestRequest request = new JavaSourceGraphIngestRequest(
                "document:200", "UserMapper.xml", xml, "commerce", "backend",
                "mapper/UserMapper.xml", "hash200",
                List.of("document:200:chunk:0", "document:200:statement:search")
        );

        List<StructuredSourceGraphAnalyzer.MyBatisStatementAnalysis> statements =
                analyzer.analyzeMyBatisStatements(request);
        SourceGraphResponse graph = analyzer.analyze(request);

        assertThat(statements).hasSize(1);
        assertThat(statements.get(0).dynamic()).isTrue();
        assertThat(statements.get(0).sqlTemplate()).contains("u.id", "FROM app.users", "FOREACH ids AS id");
        assertThat(statements.get(0).conditions())
                .contains("name != null", "active != null", "otherwise", "ids != null");
        assertThat(statements.get(0).sqlVariants()).hasSizeGreaterThan(1);
        assertThat(graph.nodes()).anySatisfy(node -> {
            if (node.id().equals("statement:commerce:com.example.UserMapper:search")) {
                assertThat(node.properties()).containsEntry("dynamic", true);
                assertThat(node.properties().get("sqlVariants")).isInstanceOf(List.class);
            }
        });
        assertThat(graph.relationships()).anyMatch(relationship ->
                relationship.sourceId().equals("document:200:statement:search")
                        && relationship.targetId().equals("statement:commerce:com.example.UserMapper:search")
                        && relationship.type().equals("EVIDENCE_FOR"));
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
