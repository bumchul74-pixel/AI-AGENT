package com.hanwha.ai.neo4jexplorer.repository;

import com.hanwha.ai.neo4jexplorer.dto.Neo4jNodeDetailResponse;
import com.hanwha.ai.neo4jexplorer.dto.Neo4jNodeSummary;
import com.hanwha.ai.neo4jexplorer.dto.Neo4jRelationshipResponse;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

@Repository
public class Neo4jClientExplorerRepository implements Neo4jExplorerRepository {
    private static final int RELATIONSHIP_LIMIT = 500;
    private static final Set<String> SENSITIVE_KEY_PARTS = Set.of(
            "password", "secret", "token", "credential", "apikey", "api_key"
    );
    private final Neo4jClient neo4jClient;

    public Neo4jClientExplorerRepository(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    @Override
    public long count(String label, String keyword) {
        return neo4jClient.query("""
                MATCH (node)
                WHERE ($label = '' OR $label IN labels(node))
                  AND ($keyword = ''
                    OR any(nodeLabel IN labels(node) WHERE toLower(nodeLabel) CONTAINS $keyword)
                    OR any(propertyKey IN keys(node)
                           WHERE toLower(toString(node[propertyKey])) CONTAINS $keyword))
                RETURN count(node) AS total
                """)
                .bind(label).to("label")
                .bind(keyword).to("keyword")
                .fetch().one()
                .map(row -> longNumber(row.get("total")))
                .orElse(0L);
    }

    @Override
    public List<Neo4jNodeSummary> findPage(String label, String keyword, int offset, int size) {
        Collection<Map<String, Object>> rows = neo4jClient.query("""
                MATCH (node)
                WHERE ($label = '' OR $label IN labels(node))
                  AND ($keyword = ''
                    OR any(nodeLabel IN labels(node) WHERE toLower(nodeLabel) CONTAINS $keyword)
                    OR any(propertyKey IN keys(node)
                           WHERE toLower(toString(node[propertyKey])) CONTAINS $keyword))
                OPTIONAL MATCH (node)-[relationship]-()
                WITH node, count(relationship) AS relationshipCount
                RETURN elementId(node) AS elementId,
                       labels(node) AS labels,
                       coalesce(node.name, node.fileName, node.simpleName, node.fqn,
                                node.uid, node.title, elementId(node)) AS displayName,
                       size(keys(node)) AS propertyCount,
                       relationshipCount
                ORDER BY toLower(toString(displayName)), elementId
                SKIP $offset LIMIT $size
                """)
                .bind(label).to("label")
                .bind(keyword).to("keyword")
                .bind(offset).to("offset")
                .bind(size).to("size")
                .fetch().all();
        return rows.stream().map(this::toSummary).toList();
    }

    @Override
    public Optional<Neo4jNodeDetailResponse> findDetail(String elementId) {
        Optional<Map<String, Object>> node = neo4jClient.query("""
                MATCH (node)
                WHERE elementId(node) = $elementId
                OPTIONAL MATCH (node)-[relationship]-()
                RETURN elementId(node) AS elementId,
                       labels(node) AS labels,
                       coalesce(node.name, node.fileName, node.simpleName, node.fqn,
                                node.uid, node.title, elementId(node)) AS displayName,
                       properties(node) AS properties,
                       count(relationship) AS relationshipCount
                LIMIT 1
                """)
                .bind(elementId).to("elementId")
                .fetch().one();
        if (node.isEmpty()) return Optional.empty();

        Collection<Map<String, Object>> relationshipRows = neo4jClient.query("""
                MATCH (node)-[relationship]-(other)
                WHERE elementId(node) = $elementId
                RETURN elementId(relationship) AS elementId,
                       type(relationship) AS type,
                       CASE WHEN elementId(startNode(relationship)) = $elementId
                            THEN 'OUTGOING' ELSE 'INCOMING' END AS direction,
                       properties(relationship) AS properties,
                       elementId(other) AS otherElementId,
                       labels(other) AS otherLabels,
                       coalesce(other.name, other.fileName, other.simpleName, other.fqn,
                                other.uid, other.title, elementId(other)) AS otherDisplayName
                ORDER BY type, toLower(toString(otherDisplayName)), elementId
                LIMIT $relationshipLimit
                """)
                .bind(elementId).to("elementId")
                .bind(RELATIONSHIP_LIMIT).to("relationshipLimit")
                .fetch().all();
        Map<String, Object> row = node.orElseThrow();
        return Optional.of(new Neo4jNodeDetailResponse(
                text(row.get("elementId")), stringList(row.get("labels")), text(row.get("displayName")),
                maskedProperties(row.get("properties")), longNumber(row.get("relationshipCount")),
                relationshipRows.stream().map(this::toRelationship).toList()
        ));
    }

    private Neo4jNodeSummary toSummary(Map<String, Object> row) {
        return new Neo4jNodeSummary(text(row.get("elementId")), stringList(row.get("labels")),
                text(row.get("displayName")), intNumber(row.get("propertyCount")),
                longNumber(row.get("relationshipCount")));
    }

    private Neo4jRelationshipResponse toRelationship(Map<String, Object> row) {
        return new Neo4jRelationshipResponse(text(row.get("elementId")), text(row.get("type")),
                text(row.get("direction")), maskedProperties(row.get("properties")),
                text(row.get("otherElementId")), stringList(row.get("otherLabels")),
                text(row.get("otherDisplayName")));
    }

    private Map<String, Object> maskedProperties(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, propertyValue) -> {
            String name = String.valueOf(key);
            result.put(name, isSensitive(name) ? "******" : propertyValue);
        });
        return result;
    }

    private boolean isSensitive(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return SENSITIVE_KEY_PARTS.stream().anyMatch(normalized::contains);
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof Collection<?> values)) return List.of();
        return values.stream().map(String::valueOf).toList();
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private int intNumber(Object value) { return value instanceof Number number ? number.intValue() : 0; }
    private static long longNumber(Object value) { return value instanceof Number number ? number.longValue() : 0L; }
}