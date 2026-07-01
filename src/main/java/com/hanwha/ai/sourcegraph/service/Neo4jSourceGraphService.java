package com.hanwha.ai.sourcegraph.service;

import com.hanwha.ai.generation.domain.GenerationHistory;
import com.hanwha.ai.sourcegraph.config.SourceGraphProperties;
import com.hanwha.ai.sourcegraph.dto.JavaSourceGraphIngestRequest;
import com.hanwha.ai.sourcegraph.dto.SourceGraphIndexResult;
import com.hanwha.ai.sourcegraph.dto.SourceGraphNodeResponse;
import com.hanwha.ai.sourcegraph.dto.SourceGraphRelationshipResponse;
import com.hanwha.ai.sourcegraph.dto.SourceGraphResponse;
import com.hanwha.ai.sourcegraph.exception.NoJavaTypeFoundException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class Neo4jSourceGraphService implements SourceGraphService {
    private static final Logger log = LoggerFactory.getLogger(Neo4jSourceGraphService.class);
    private static final Set<String> DEPENDENCY_RELATIONSHIPS = Set.of(
            "IMPORTS", "EXTENDS", "IMPLEMENTS", "INJECTS", "USES"
    );

    private final Neo4jClient neo4jClient;
    private final SourceGraphProperties properties;
    private final JavaSourceGraphAnalyzer analyzer;
    private final AtomicBoolean constraintsInitialized = new AtomicBoolean(false);

    public Neo4jSourceGraphService(
            Neo4jClient neo4jClient,
            SourceGraphProperties properties,
            JavaSourceGraphAnalyzer analyzer
    ) {
        this.neo4jClient = neo4jClient;
        this.properties = properties;
        this.analyzer = analyzer;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeConstraints() {
        if (!properties.enabled()) {
            return;
        }

        try {
        } catch (Exception exception) {
            log.debug("Neo4j source graph constraints were not initialized.", exception);
        }
    }

    @Override
    public SourceGraphIndexResult index(GenerationHistory history) {
        LocalDateTime indexedAt = LocalDateTime.now();
        if (!properties.enabled()) {
            return SourceGraphIndexResult.skipped(indexedAt, "Source graph indexing is disabled.");
        }
        if (history == null || history.getId() == null) {
            return SourceGraphIndexResult.failed(indexedAt, "Generation history id is required.");
        }

        try {
            SourceGraphResponse graph = analyzer.analyze(history);
            deleteHistoryGraph(history.getId());
            deleteGraphByGraphKey(graphKey(graph));
            writeGraph(graph);
            return SourceGraphIndexResult.success(indexedAt);
        } catch (Exception exception) {
            log.warn("Failed to index generation history {} into Neo4j.", history.getId(), exception);
            return SourceGraphIndexResult.failed(indexedAt, abbreviate(exception.getMessage()));
        }
    }

    @Override
    public SourceGraphIndexResult indexJavaSource(JavaSourceGraphIngestRequest request) {
        LocalDateTime indexedAt = LocalDateTime.now();
        if (!properties.enabled()) {
            return SourceGraphIndexResult.skipped(indexedAt, "Source graph indexing is disabled.");
        }

        try {
            SourceGraphResponse graph = analyzer.analyzeJavaSource(request);
            deleteGraphByGraphKey(graphKey(graph));
            writeGraph(graph);
            return SourceGraphIndexResult.success(indexedAt);
        } catch (NoJavaTypeFoundException exception) {
            return SourceGraphIndexResult.skipped(indexedAt, exception.getMessage());
        } catch (Exception exception) {
            String source = request == null ? "" : request.source();
            log.warn("Failed to index Java source into Neo4j. source={}", source, exception);
            return SourceGraphIndexResult.failed(indexedAt, abbreviate(exception.getMessage()));
        }
    }

    @Override
    public int countJavaSourceFiles() {
        if (!properties.enabled()) {
            return 0;
        }

        try {
            Collection<Map<String, Object>> rows = neo4jClient.query("""
                    MATCH (s:SourceFile)
                    WHERE s.uid IS NOT NULL
                    RETURN count(DISTINCT s.uid) AS count
                    """).fetch().all();
            return rows.stream()
                    .findFirst()
                    .map(row -> intValue(row.get("count")))
                    .orElse(0);
        } catch (Exception exception) {
            log.warn("Failed to count Java source files in Neo4j.", exception);
            return 0;
        }
    }
    @Override
    public SourceGraphResponse findOverview(String query, int limit) {
        if (!properties.enabled()) {
            return SourceGraphResponse.empty(null);
        }

        int safeLimit = Math.max(20, Math.min(limit, 1500));
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("limit", safeLimit);

        String nodeCypher;
        if (StringUtils.hasText(query)) {
            parameters.put("query", query.trim().toLowerCase(Locale.ROOT));
            nodeCypher = """
                    MATCH (matched)
                    WHERE matched.uid IS NOT NULL
                      AND (
                        toLower(coalesce(matched.name, '')) CONTAINS $query
                        OR toLower(coalesce(matched.fileName, '')) CONTAINS $query
                        OR toLower(coalesce(matched.simpleName, '')) CONTAINS $query
                        OR toLower(coalesce(matched.fqn, '')) CONTAINS $query
                        OR toLower(coalesce(matched.source, '')) CONTAINS $query
                        OR toLower(coalesce(matched.sourceKind, '')) CONTAINS $query
                        OR any(label IN labels(matched) WHERE toLower(label) CONTAINS $query)
                      )
                    WITH collect(DISTINCT matched.graphKey)[0..20] AS graphKeys,
                         collect(DISTINCT matched.uid) AS matchedIds
                    MATCH (n)
                    WHERE n.uid IS NOT NULL
                      AND (n.graphKey IN graphKeys OR n.uid IN matchedIds)
                    RETURN n.uid AS id,
                           head(labels(n)) AS label,
                           coalesce(n.name, n.fileName, n.simpleName, n.fqn, n.source, n.uid) AS name,
                           properties(n) AS properties
                    ORDER BY label, name
                    LIMIT $limit
                    """;
        } else {
            nodeCypher = """
                    MATCH (n)
                    WHERE n.uid IS NOT NULL
                    RETURN n.uid AS id,
                           head(labels(n)) AS label,
                           coalesce(n.name, n.fileName, n.simpleName, n.fqn, n.source, n.uid) AS name,
                           properties(n) AS properties
                    ORDER BY label, name
                    LIMIT $limit
                    """;
        }

        List<SourceGraphNodeResponse> nodes = fetchNodes(nodeCypher, parameters);
        return new SourceGraphResponse(null, nodes, fetchRelationshipsForNodes(nodes));
    }
    @Override
    public SourceGraphResponse findByHistoryId(Long historyId) {
        if (historyId == null || !properties.enabled()) {
            return SourceGraphResponse.empty(historyId);
        }

        List<SourceGraphNodeResponse> nodes = fetchNodes("""
                MATCH (n)
                WHERE n.historyId = $historyId
                RETURN n.uid AS id,
                       head(labels(n)) AS label,
                       coalesce(n.name, n.fileName, n.simpleName, toString(n.id)) AS name,
                       properties(n) AS properties
                ORDER BY label, name
                """, Map.of("historyId", historyId));
        List<SourceGraphRelationshipResponse> relationships = fetchRelationships("""
                MATCH (a)-[r]->(b)
                WHERE a.historyId = $historyId AND b.historyId = $historyId
                RETURN a.uid AS sourceId,
                       b.uid AS targetId,
                       type(r) AS type,
                       properties(r) AS properties
                ORDER BY type, sourceId, targetId
                """, Map.of("historyId", historyId));
        return new SourceGraphResponse(historyId, nodes, relationships);
    }

    @Override
    public SourceGraphResponse findDependencies(String fqn) {
        return findTypeNeighborhood(fqn, "OUTGOING");
    }

    @Override
    public SourceGraphResponse findImpacts(String fqn) {
        return findTypeNeighborhood(fqn, "INCOMING");
    }

    private SourceGraphResponse findTypeNeighborhood(String fqn, String direction) {
        if (!StringUtils.hasText(fqn) || !properties.enabled()) {
            return SourceGraphResponse.empty(null);
        }

        String relationshipPattern = "OUTGOING".equals(direction) ? "(source)-[r]->(target)" : "(target)-[r]->(source)";
        Collection<Map<String, Object>> rows = neo4jClient.query("""
                MATCH %s
                WHERE source:JavaType AND target:JavaType AND source.fqn = $fqn
                  AND type(r) IN $relationshipTypes
                RETURN source.uid AS sourceId,
                       source.simpleName AS sourceName,
                       properties(source) AS sourceProperties,
                       target.uid AS targetId,
                       target.simpleName AS targetName,
                       properties(target) AS targetProperties,
                       type(r) AS relationshipType,
                       properties(r) AS relationshipProperties
                ORDER BY relationshipType, targetName
                """.formatted(relationshipPattern))
                .bind(fqn.trim()).to("fqn")
                .bind(List.copyOf(DEPENDENCY_RELATIONSHIPS)).to("relationshipTypes")
                .fetch()
                .all();

        Map<String, SourceGraphNodeResponse> nodes = new LinkedHashMap<>();
        Set<SourceGraphRelationshipResponse> relationships = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            String sourceId = stringValue(row.get("sourceId"));
            String targetId = stringValue(row.get("targetId"));
            nodes.putIfAbsent(sourceId, new SourceGraphNodeResponse(
                    sourceId,
                    "JavaType",
                    stringValue(row.get("sourceName")),
                    mapValue(row.get("sourceProperties"))
            ));
            nodes.putIfAbsent(targetId, new SourceGraphNodeResponse(
                    targetId,
                    "JavaType",
                    stringValue(row.get("targetName")),
                    mapValue(row.get("targetProperties"))
            ));
            relationships.add(new SourceGraphRelationshipResponse(
                    sourceId,
                    targetId,
                    stringValue(row.get("relationshipType")),
                    mapValue(row.get("relationshipProperties"))
            ));
        }
        return new SourceGraphResponse(null, List.copyOf(nodes.values()), List.copyOf(relationships));
    }

    private void ensureConstraints() {
        if (constraintsInitialized.get()) {
            return;
        }

        run("CREATE CONSTRAINT generation_id IF NOT EXISTS FOR (g:Generation) REQUIRE g.id IS UNIQUE", Map.of());
        run("CREATE CONSTRAINT rag_source_uid IF NOT EXISTS FOR (r:RagSource) REQUIRE r.uid IS UNIQUE", Map.of());
        run("CREATE CONSTRAINT source_file_uid IF NOT EXISTS FOR (s:SourceFile) REQUIRE s.uid IS UNIQUE", Map.of());
        run("CREATE CONSTRAINT java_type_uid IF NOT EXISTS FOR (t:JavaType) REQUIRE t.uid IS UNIQUE", Map.of());
        run("CREATE CONSTRAINT method_uid IF NOT EXISTS FOR (m:Method) REQUIRE m.uid IS UNIQUE", Map.of());
        constraintsInitialized.set(true);
    }

    private void deleteHistoryGraph(Long historyId) {
        run("""
                MATCH (n)
                WHERE n.historyId = $historyId
                DETACH DELETE n
                """, Map.of("historyId", historyId));
    }

    private void deleteGraphByGraphKey(String graphKey) {
        run("""
                MATCH (n)
                WHERE n.graphKey = $graphKey
                DETACH DELETE n
                """, Map.of("graphKey", graphKey));
    }

    private void writeGraph(SourceGraphResponse graph) {
        for (SourceGraphNodeResponse node : graph.nodes()) {
            writeNode(node);
        }
        for (SourceGraphRelationshipResponse relationship : graph.relationships()) {
            writeRelationship(relationship);
        }
    }

    private void writeNode(SourceGraphNodeResponse node) {
        String label = safeLabel(node.label());
        Map<String, Object> properties = nonNullProperties(node.properties());
        if ("Generation".equals(label)) {
            run("""
                    MERGE (n:Generation {id: $id})
                    SET n += $properties
                    """, Map.of(
                    "id", properties.get("id"),
                    "properties", properties
            ));
            return;
        }

        run("""
                MERGE (n:%s {uid: $uid})
                SET n += $properties
                """.formatted(label), Map.of(
                "uid", node.id(),
                "properties", properties
        ));
    }

    private void writeRelationship(SourceGraphRelationshipResponse relationship) {
        String type = safeRelationshipType(relationship.type());
        Map<String, Object> properties = nonNullProperties(relationship.properties());
        run("""
                MATCH (source {uid: $sourceId})
                MATCH (target {uid: $targetId})
                MERGE (source)-[r:%s]->(target)
                SET r += $properties
                """.formatted(type), Map.of(
                "sourceId", relationship.sourceId(),
                "targetId", relationship.targetId(),
                "properties", properties
        ));
    }

    private List<SourceGraphNodeResponse> fetchNodes(String cypher, Map<String, Object> parameters) {
        Collection<Map<String, Object>> rows = neo4jClient.query(cypher).bindAll(parameters).fetch().all();
        return rows.stream()
                .map(row -> new SourceGraphNodeResponse(
                        stringValue(row.get("id")),
                        stringValue(row.get("label")),
                        stringValue(row.get("name")),
                        mapValue(row.get("properties"))
                ))
                .toList();
    }

    private List<SourceGraphRelationshipResponse> fetchRelationshipsForNodes(List<SourceGraphNodeResponse> nodes) {
        List<String> nodeIds = nodes.stream()
                .map(SourceGraphNodeResponse::id)
                .filter(StringUtils::hasText)
                .toList();
        if (nodeIds.isEmpty()) {
            return List.of();
        }

        return fetchRelationships("""
                MATCH (a)-[r]->(b)
                WHERE a.uid IN $nodeIds AND b.uid IN $nodeIds
                RETURN a.uid AS sourceId,
                       b.uid AS targetId,
                       type(r) AS type,
                       properties(r) AS properties
                ORDER BY type, sourceId, targetId
                LIMIT $relationshipLimit
                """, Map.of(
                "nodeIds", nodeIds,
                "relationshipLimit", Math.max(200, Math.min(nodeIds.size() * 6, 5000))
        ));
    }
    private List<SourceGraphRelationshipResponse> fetchRelationships(String cypher, Map<String, Object> parameters) {
        Collection<Map<String, Object>> rows = neo4jClient.query(cypher).bindAll(parameters).fetch().all();
        return rows.stream()
                .map(row -> new SourceGraphRelationshipResponse(
                        stringValue(row.get("sourceId")),
                        stringValue(row.get("targetId")),
                        stringValue(row.get("type")),
                        mapValue(row.get("properties"))
                ))
                .toList();
    }

    private void run(String cypher, Map<String, Object> parameters) {
        neo4jClient.query(cypher).bindAll(parameters).run();
    }

    private String graphKey(SourceGraphResponse graph) {
        return graph.nodes().stream()
                .map(SourceGraphNodeResponse::properties)
                .map(properties -> properties.get("graphKey"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Source graph key is required."));
    }

    private Map<String, Object> nonNullProperties(Map<String, Object> properties) {
        Map<String, Object> nonNull = new LinkedHashMap<>();
        if (properties == null) {
            return nonNull;
        }
        properties.forEach((key, value) -> {
            if (key != null && value != null) {
                nonNull.put(key, value);
            }
        });
        return nonNull;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> target = new LinkedHashMap<>();
            source.forEach((key, mapValue) -> {
                if (key != null && mapValue != null) {
                    target.put(String.valueOf(key), mapValue);
                }
            });
            return target;
        }
        return Map.of();
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
    private String safeLabel(String label) {
        if (Set.of("Generation", "RagSource", "SourceFile", "JavaType", "Method").contains(label)) {
            return label;
        }
        return "JavaType";
    }

    private String safeRelationshipType(String type) {
        String normalized = StringUtils.hasText(type) ? type.trim().toUpperCase(Locale.ROOT) : "USES";
        if (!normalized.matches("[A-Z_][A-Z0-9_]*")) {
            return "USES";
        }
        return normalized;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String abbreviate(String message) {
        if (!StringUtils.hasText(message)) {
            return "Unknown Neo4j indexing error.";
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
