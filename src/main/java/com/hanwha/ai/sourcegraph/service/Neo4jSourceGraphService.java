package com.hanwha.ai.sourcegraph.service;

import com.hanwha.ai.document.domain.RagDocument;
import com.hanwha.ai.document.service.RagDocumentRepository;
import com.hanwha.ai.generation.domain.GenerationHistory;
import com.hanwha.ai.generation.repository.GenerationRepository;
import com.hanwha.ai.sourcegraph.config.SourceGraphProperties;
import com.hanwha.ai.sourcegraph.dto.JavaSourceGraphIngestRequest;
import com.hanwha.ai.sourcegraph.dto.SourceGraphIndexResult;
import com.hanwha.ai.sourcegraph.dto.SourceGraphNodeResponse;
import com.hanwha.ai.sourcegraph.dto.SourceGraphNodeSourceResponse;
import com.hanwha.ai.sourcegraph.dto.SourceGraphRelationshipResponse;
import com.hanwha.ai.sourcegraph.dto.SourceGraphResponse;
import com.hanwha.ai.sourcegraph.dto.SourceGraphSourceResponse;
import com.hanwha.ai.sourcegraph.domain.SourceOntology;
import com.hanwha.ai.sourcegraph.exception.NoJavaTypeFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
            "IMPORTS", "EXTENDS", "IMPLEMENTS", "INJECTS", "USES", "USES_DTO", "CALLS",
            "READS_FROM", "WRITES_TO", "MAPS_TO", "EXECUTES", "HAS_STATEMENT", "HAS_MAPPER_XML"
    );
    private static final Set<String> HYBRID_SEARCH_RELATIONSHIPS = Set.of(
            "DECLARES", "HAS_METHOD", "HAS_FIELD", "IMPORTS", "EXTENDS", "IMPLEMENTS",
            "INJECTS", "USES", "USES_DTO", "CALLS", "HANDLED_BY", "READS_FROM",
            "WRITES_TO", "MAPS_TO", "CONFORMS_TO", "VIOLATES", "EXECUTES",
            "HAS_STATEMENT", "HAS_MAPPER_XML", "HAS_COLUMN", "REFERENCES_COLUMN",
            "DESCRIBES", "EVIDENCE_FOR", "BASED_ON", "HAS_SOURCE", "DEFINES",
            "CONFIGURES", "ACTIVATES"
    );
    private static final List<String> OVERVIEW_NODE_LABELS = List.of(
            "Project", "Module", "Package", "SourceFile", "JavaType", "Method", "Field",
            "ApiEndpoint", "DatabaseTable", "DatabaseColumn", "SqlStatement", "Document",
            "Chunk", "StandardRule", "StandardTemplate", "Generation", "ConfigurationFile",
            "ConfigurationProperty", "Bean", "Profile", "Provider"
    );

    private final Neo4jClient neo4jClient;
    private final SourceGraphProperties properties;
    private final JavaSourceGraphAnalyzer analyzer;
    private final GenerationRepository generationRepository;
    private final RagDocumentRepository ragDocumentRepository;
    private final AtomicBoolean constraintsInitialized = new AtomicBoolean(false);

    public Neo4jSourceGraphService(
            Neo4jClient neo4jClient,
            SourceGraphProperties properties,
            JavaSourceGraphAnalyzer analyzer,
            GenerationRepository generationRepository,
            RagDocumentRepository ragDocumentRepository
    ) {
        this.neo4jClient = neo4jClient;
        this.properties = properties;
        this.analyzer = analyzer;
        this.generationRepository = generationRepository;
        this.ragDocumentRepository = ragDocumentRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeConstraints() {
        if (!properties.enabled()) {
            return;
        }

        try {
            ensureConstraints();
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
            SourceGraphResponse graph = analyzer.analyzeSource(request);
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
                    WHERE s.uid IS NOT NULL AND toLower(coalesce(s.fileName, '')) ENDS WITH '.java'
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
        List<SourceGraphNodeResponse> nodes = fetchBalancedOverviewNodes(query, safeLimit);
        return new SourceGraphResponse(null, nodes, fetchRelationshipsForNodes(nodes));
    }

    @Override
    public SourceGraphResponse findOverview(String query, int limit, String projectId) {
        if (!StringUtils.hasText(projectId)) return findOverview(query, limit);
        if (!properties.enabled()) return SourceGraphResponse.empty(null);
        int safeLimit = Math.max(20, Math.min(limit, 1500));
        boolean hasQuery = StringUtils.hasText(query);
        String queryClause = hasQuery ? """
                  AND (
                    toLower(coalesce(n.name, '')) CONTAINS $query
                    OR toLower(coalesce(n.fileName, '')) CONTAINS $query
                    OR toLower(coalesce(n.simpleName, '')) CONTAINS $query
                    OR toLower(coalesce(n.fqn, '')) CONTAINS $query
                    OR any(label IN labels(n) WHERE toLower(label) CONTAINS $query)
                  )
                """ : "";
        String cypher = """
                MATCH (n)
                WHERE n.uid IS NOT NULL AND n.projectId = $projectId
                """ + queryClause + """
                RETURN n.uid AS id,
                       head([nodeLabel IN labels(n) WHERE nodeLabel <> 'SourceGraphEntity']) AS label,
                       coalesce(n.name, n.fileName, n.simpleName, n.fqn, n.source, n.uid) AS name,
                       properties(n) AS properties
                ORDER BY name
                LIMIT $limit
                """;
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("projectId", projectId.trim());
        parameters.put("limit", safeLimit);
        if (hasQuery) parameters.put("query", query.trim().toLowerCase(Locale.ROOT));
        List<SourceGraphNodeResponse> nodes = fetchNodes(cypher, parameters);
        return new SourceGraphResponse(null, nodes, fetchRelationshipsForNodes(nodes));
    }

    private List<SourceGraphNodeResponse> fetchBalancedOverviewNodes(String query, int limit) {
        Map<String, SourceGraphNodeResponse> nodes = new LinkedHashMap<>();
        boolean hasQuery = StringUtils.hasText(query);
        int labelLimit = Math.max(1, (int) Math.ceil((double) limit / OVERVIEW_NODE_LABELS.size()));
        String labelCypher = balancedOverviewLabelCypher(hasQuery);

        for (String label : OVERVIEW_NODE_LABELS) {
            Map<String, Object> parameters = overviewParameters(query);
            parameters.put("label", label);
            parameters.put("labelLimit", labelLimit);
            fetchNodes(labelCypher, parameters).forEach(node -> nodes.putIfAbsent(node.id(), node));
        }

        int remainingLimit = limit - nodes.size();
        if (remainingLimit > 0) {
            Map<String, Object> parameters = overviewParameters(query);
            parameters.put("selectedIds", List.copyOf(nodes.keySet()));
            parameters.put("remainingLimit", remainingLimit);
            fetchNodes(remainingOverviewCypher(hasQuery), parameters).forEach(node -> nodes.putIfAbsent(node.id(), node));
        }

        return List.copyOf(nodes.values());
    }

    private Map<String, Object> overviewParameters(String query) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        if (StringUtils.hasText(query)) {
            parameters.put("query", query.trim().toLowerCase(Locale.ROOT));
        }
        return parameters;
    }

    private String balancedOverviewLabelCypher(boolean hasQuery) {
        if (hasQuery) {
            return """
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
                      AND $label IN labels(n)
                      AND (n.graphKey IN graphKeys OR n.uid IN matchedIds)
                    RETURN n.uid AS id,
                           head([nodeLabel IN labels(n) WHERE nodeLabel <> 'SourceGraphEntity']) AS label,
                           coalesce(n.name, n.fileName, n.simpleName, n.fqn, n.source, n.uid) AS name,
                           properties(n) AS properties
                    ORDER BY name
                    LIMIT $labelLimit
                    """;
        }

        return """
                MATCH (n)
                WHERE n.uid IS NOT NULL
                  AND $label IN labels(n)
                RETURN n.uid AS id,
                       head([nodeLabel IN labels(n) WHERE nodeLabel <> 'SourceGraphEntity']) AS label,
                       coalesce(n.name, n.fileName, n.simpleName, n.fqn, n.source, n.uid) AS name,
                       properties(n) AS properties
                ORDER BY name
                LIMIT $labelLimit
                """;
    }

    private String remainingOverviewCypher(boolean hasQuery) {
        if (hasQuery) {
            return """
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
                      AND NOT (n.uid IN $selectedIds)
                    RETURN n.uid AS id,
                           head([nodeLabel IN labels(n) WHERE nodeLabel <> 'SourceGraphEntity']) AS label,
                           coalesce(n.name, n.fileName, n.simpleName, n.fqn, n.source, n.uid) AS name,
                           properties(n) AS properties
                    ORDER BY label, name
                    LIMIT $remainingLimit
                    """;
        }

        return """
                MATCH (n)
                WHERE n.uid IS NOT NULL
                  AND NOT (n.uid IN $selectedIds)
                RETURN n.uid AS id,
                       head([nodeLabel IN labels(n) WHERE nodeLabel <> 'SourceGraphEntity']) AS label,
                       coalesce(n.name, n.fileName, n.simpleName, n.fqn, n.source, n.uid) AS name,
                       properties(n) AS properties
                ORDER BY label, name
                LIMIT $remainingLimit
                """;
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
                       head([nodeLabel IN labels(n) WHERE nodeLabel <> 'SourceGraphEntity']) AS label,
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

    @Override
    public SourceGraphResponse findNeighborhoodByEntityIds(List<String> entityIds, int depth) {
        if (!properties.enabled() || entityIds == null || entityIds.isEmpty()) {
            return SourceGraphResponse.empty(null);
        }
        List<String> normalizedIds = entityIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(100)
                .toList();
        if (normalizedIds.isEmpty()) {
            return SourceGraphResponse.empty(null);
        }
        int safeDepth = Math.max(1, Math.min(depth, 6));
        List<SourceGraphNodeResponse> nodes = fetchNodes("""
                MATCH path=(seed:SourceGraphEntity)-[pathRelationships*0..%d]-(node:SourceGraphEntity)
                WHERE seed.uid IN $entityIds
                  AND all(relationship IN pathRelationships
                          WHERE type(relationship) IN $relationshipTypes)
                WITH DISTINCT node
                LIMIT 600
                RETURN node.uid AS id,
                       head([nodeLabel IN labels(node) WHERE nodeLabel <> 'SourceGraphEntity']) AS label,
                       coalesce(node.name, node.simpleName, node.fileName, node.fqn, node.uid) AS name,
                       properties(node) AS properties
                """.formatted(safeDepth), Map.of(
                "entityIds", normalizedIds,
                "relationshipTypes", List.copyOf(HYBRID_SEARCH_RELATIONSHIPS)
        ));
        return new SourceGraphResponse(null, nodes, fetchRelationshipsForNodes(nodes));
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

    @Override
    public SourceGraphNodeSourceResponse findNodeSource(String nodeId) {
        if (!StringUtils.hasText(nodeId)) {
            return SourceGraphNodeSourceResponse.unavailable(nodeId, "Source graph node id is required.");
        }
        if (!properties.enabled()) {
            return SourceGraphNodeSourceResponse.unavailable(nodeId, "Source graph indexing is disabled.");
        }

        try {
            Collection<Map<String, Object>> rows = neo4jClient.query("""
                    MATCH (node)
                    WHERE node.uid = $nodeId
                    OPTIONAL MATCH (declaringFile:SourceFile)-[:DECLARES]->(node)
                    OPTIONAL MATCH (declaringType:JavaType)-[:HAS_METHOD]->(node)
                    OPTIONAL MATCH (methodFile:SourceFile)-[:DECLARES]->(declaringType)
                    RETURN node.uid AS nodeId,
                           head([nodeLabel IN labels(node) WHERE nodeLabel <> 'SourceGraphEntity']) AS label,
                           coalesce(node.name, node.simpleName, node.fileName, node.fqn, node.uid) AS name,
                           properties(node) AS nodeProperties,
                           properties(declaringFile) AS declaringFileProperties,
                           properties(declaringType) AS declaringTypeProperties,
                           properties(methodFile) AS methodFileProperties
                    LIMIT 1
                    """)
                    .bind(nodeId.trim()).to("nodeId")
                    .fetch()
                    .all();

            return rows.stream()
                    .findFirst()
                    .map(this::toNodeSourceResponse)
                    .orElseGet(() -> SourceGraphNodeSourceResponse.unavailable(nodeId, "Source graph node was not found."));
        } catch (Exception exception) {
            log.warn("Failed to fetch source content for source graph node {}.", nodeId, exception);
            return SourceGraphNodeSourceResponse.unavailable(nodeId, "Source content request failed.");
        }
    }

    @Override
    public void deleteBySourceKey(String sourceKey) {
        if (!StringUtils.hasText(sourceKey)) {
            return;
        }
        String graphKey = "rag-source:" + UUID.nameUUIDFromBytes(
                sourceKey.trim().getBytes(StandardCharsets.UTF_8)
        );
        deleteGraphByGraphKey(graphKey);
    }

    @Override
    public void deleteByGraphKey(String graphKey) {
        if (StringUtils.hasText(graphKey)) {
            deleteGraphByGraphKey(graphKey.trim());
        }
    }

    @Override
    public List<SourceGraphSourceResponse> findIndexedSources() {
        if (!properties.enabled()) {
            return List.of();
        }
        Collection<Map<String, Object>> rows = neo4jClient.query("""
                MATCH (n)
                WHERE n.graphKey IS NOT NULL AND trim(toString(n.graphKey)) <> ''
                WITH n.graphKey AS graphKey,
                     coalesce(max(n.sourceKey), max(n.source), n.graphKey) AS sourceKey,
                     coalesce(max(n.projectId), '') AS projectId,
                     coalesce(max(n.fileName), max(n.name), n.graphKey) AS fileName,
                     count(n) AS nodeCount
                RETURN sourceKey, graphKey, projectId, fileName, nodeCount
                ORDER BY toLower(fileName), sourceKey
                """).fetch().all();
        return rows.stream()
                .map(row -> new SourceGraphSourceResponse(
                        stringValue(row.get("sourceKey")),
                        stringValue(row.get("graphKey")),
                        stringValue(row.get("projectId")),
                        stringValue(row.get("fileName")),
                        intValue(row.get("nodeCount"))
                ))
                .toList();
    }

    private SourceGraphNodeSourceResponse toNodeSourceResponse(Map<String, Object> row) {
        Map<String, Object> nodeProperties = mapValue(row.get("nodeProperties"), true);
        Map<String, Object> declaringFileProperties = mapValue(row.get("declaringFileProperties"), true);
        Map<String, Object> declaringTypeProperties = mapValue(row.get("declaringTypeProperties"), true);
        Map<String, Object> methodFileProperties = mapValue(row.get("methodFileProperties"), true);

        String nodeId = stringValue(row.get("nodeId"));
        String label = stringValue(row.get("label"));
        String name = stringValue(row.get("name"));
        String fqn = firstText(
                stringValue(nodeProperties.get("fqn")),
                stringValue(nodeProperties.get("primaryType")),
                stringValue(declaringTypeProperties.get("fqn")),
                stringValue(declaringFileProperties.get("primaryType")),
                stringValue(methodFileProperties.get("primaryType"))
        );
        String simpleName = firstText(
                stringValue(nodeProperties.get("simpleName")),
                stringValue(declaringTypeProperties.get("simpleName")),
                simpleName(fqn),
                name
        );
        String graphSourceKey = firstText(
                stringValue(nodeProperties.get("source")),
                stringValue(declaringFileProperties.get("source")),
                stringValue(declaringTypeProperties.get("source")),
                stringValue(methodFileProperties.get("source")),
                stringValue(nodeProperties.get("sourceKey")),
                stringValue(declaringFileProperties.get("sourceKey")),
                stringValue(declaringTypeProperties.get("sourceKey")),
                stringValue(methodFileProperties.get("sourceKey")),
                stringValue(nodeProperties.get("graphSourceKey")),
                stringValue(declaringFileProperties.get("graphSourceKey")),
                stringValue(methodFileProperties.get("graphSourceKey"))
        );
        DocumentFileSource documentFileSource = documentFileSource(graphSourceKey);
        String fileName = firstText(
                documentFileSource.fileName(),
                stringValue(nodeProperties.get("fileName")),
                stringValue(declaringFileProperties.get("fileName")),
                stringValue(methodFileProperties.get("fileName")),
                StringUtils.hasText(simpleName) ? simpleName + ".java" : ""
        );
        String sourceKind = firstText(
                stringValue(nodeProperties.get("sourceKind")),
                stringValue(declaringFileProperties.get("sourceKind")),
                stringValue(declaringTypeProperties.get("sourceKind")),
                stringValue(methodFileProperties.get("sourceKind"))
        );
        String content = documentFileSource.content();
        if (!StringUtils.hasText(content)) {
            content = firstText(
                    generatedHistorySource(nodeProperties, declaringFileProperties, declaringTypeProperties, methodFileProperties, fqn, simpleName),
                    stringValue(nodeProperties.get("sourceContent")),
                    stringValue(declaringFileProperties.get("sourceContent")),
                    stringValue(methodFileProperties.get("sourceContent")),
                    stringValue(declaringTypeProperties.get("sourceContent"))
            );
        }

        if (StringUtils.hasText(content)) {
            return SourceGraphNodeSourceResponse.available(
                    nodeId,
                    label,
                    name,
                    fqn,
                    fileName,
                    sourceKind,
                    documentFileSource.graphSourceKey(),
                    documentFileSource.filePath(),
                    content
            );
        }
        return new SourceGraphNodeSourceResponse(
                nodeId,
                label,
                name,
                fqn,
                fileName,
                sourceKind,
                documentFileSource.graphSourceKey(),
                documentFileSource.filePath(),
                "",
                false,
                nodeSourceUnavailableMessage(graphSourceKey, documentFileSource)
        );
    }

    private String generatedHistorySource(
            Map<String, Object> nodeProperties,
            Map<String, Object> declaringFileProperties,
            Map<String, Object> declaringTypeProperties,
            Map<String, Object> methodFileProperties,
            String fqn,
            String simpleName
    ) {
        Long historyId = firstLong(
                nodeProperties.get("historyId"),
                declaringFileProperties.get("historyId"),
                declaringTypeProperties.get("historyId"),
                methodFileProperties.get("historyId")
        );
        if (historyId == null) {
            return "";
        }

        GenerationHistory history = generationRepository.findById(historyId);
        if (history == null) {
            return "";
        }
        return analyzer.findSourceContent(history.getGeneratedCode(), fqn, simpleName);
    }

    private DocumentFileSource documentFileSource(String graphSourceKey) {
        String sourceKey = StringUtils.hasText(graphSourceKey) ? graphSourceKey.trim() : "";
        if (!StringUtils.hasText(sourceKey)) {
            return new DocumentFileSource("", "", "", "");
        }

        RagDocument document = ragDocumentRepository.findByGraphSourceKey(sourceKey);
        if (document == null) {
            return new DocumentFileSource(sourceKey, "", "", "");
        }

        String fileName = firstText(document.getOriginalFileName(), document.getStoredFileName());
        if (!StringUtils.hasText(document.getFilePath())) {
            return new DocumentFileSource(sourceKey, "", fileName, "");
        }

        Path path = Path.of(document.getFilePath()).toAbsolutePath().normalize();
        String filePath = path.toString();
        if (!Files.isRegularFile(path)) {
            log.warn("RAG document source file was not found. graphSourceKey={}, filePath={}", sourceKey, filePath);
            return new DocumentFileSource(sourceKey, filePath, fileName, "");
        }

        try {
            return new DocumentFileSource(sourceKey, filePath, fileName, Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            log.warn("Failed to read RAG document source file. graphSourceKey={}, filePath={}", sourceKey, filePath, exception);
            return new DocumentFileSource(sourceKey, filePath, fileName, "");
        }
    }

    private String nodeSourceUnavailableMessage(String graphSourceKey, DocumentFileSource documentFileSource) {
        if (StringUtils.hasText(documentFileSource.filePath())) {
            return "Physical source file was not found or could not be read: " + documentFileSource.filePath();
        }
        if (StringUtils.hasText(graphSourceKey)) {
            return "rag_document row was not found for graph_source_key: " + graphSourceKey;
        }
        return "Source content was not found for this node.";
    }

    private record DocumentFileSource(
            String graphSourceKey,
            String filePath,
            String fileName,
            String content
    ) {
    }

    private Long firstLong(Object... values) {
        for (Object value : values) {
            Long parsed = longValue(value);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String simpleName(String fqn) {
        if (!StringUtils.hasText(fqn)) {
            return "";
        }
        int index = fqn.lastIndexOf('.');
        return index >= 0 ? fqn.substring(index + 1) : fqn;
    }
    private void ensureConstraints() {
        if (constraintsInitialized.get()) {
            return;
        }

        run("MATCH (n) WHERE n.uid IS NOT NULL SET n:SourceGraphEntity", Map.of());
        run("MATCH (n:SourceGraphEntity) REMOVE n.sourceContent", Map.of());
        run("MATCH (n:StandardRule) REMOVE n.content", Map.of());
        run("""
                CREATE CONSTRAINT source_graph_entity_uid IF NOT EXISTS
                FOR (n:SourceGraphEntity) REQUIRE n.uid IS UNIQUE
                """, Map.of());
        run("""
                CREATE INDEX source_graph_project_id IF NOT EXISTS
                FOR (n:SourceGraphEntity) ON (n.projectId)
                """, Map.of());
        constraintsInitialized.set(true);
    }

    private void deleteGraphByGraphKey(String graphKey) {
        run("""
                MATCH ()-[r]->()
                WHERE r.graphKey = $graphKey
                DELETE r
                """, Map.of("graphKey", graphKey));
        run("""
                MATCH (n)
                WHERE n.graphKey = $graphKey
                  AND (n:Document OR n:SourceFile OR n:Generation OR n:RagSource)
                DETACH DELETE n
                """, Map.of("graphKey", graphKey));
        run("""
                MATCH (n)
                WHERE (n:JavaType OR n:Method OR n:Field OR n:ApiEndpoint
                       OR n:DatabaseTable OR n:DatabaseColumn OR n:SqlStatement
                       OR n:Chunk OR n:StandardRule OR n:StandardTemplate
                       OR n:ConfigurationFile OR n:ConfigurationProperty
                       OR n:Bean OR n:Profile OR n:Provider)
                  AND NOT (n)--()
                DELETE n
                """, Map.of());
        run("""
                MATCH (n:Package)
                WHERE NOT (n)-[:CONTAINS]->(:SourceFile)
                DETACH DELETE n
                """, Map.of());
        run("""
                MATCH (n:Module)
                WHERE NOT (n)-[:CONTAINS]->(:Package)
                DETACH DELETE n
                """, Map.of());
        run("""
                MATCH (n:Project)
                WHERE NOT (n)-[:CONTAINS]->(:Module)
                DETACH DELETE n
                """, Map.of());
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
                    MERGE (n:SourceGraphEntity:Generation {uid: $uid})
                    SET n += $properties
                    REMOVE n.sourceContent, n.content
                    """, Map.of(
                    "uid", node.id(),
                    "properties", properties
            ));
            return;
        }

        String classification = classificationLabel(label, properties);
        String labels = StringUtils.hasText(classification)
                ? label + ":" + classification
                : label;
        run("""
                MERGE (n:SourceGraphEntity:%s {uid: $uid})
                SET n += $properties
                REMOVE n.sourceContent, n.content
                """.formatted(labels), Map.of(
                "uid", node.id(),
                "properties", properties
        ));
    }

    private void writeRelationship(SourceGraphRelationshipResponse relationship) {
        String type = safeRelationshipType(relationship.type());
        Map<String, Object> properties = nonNullProperties(relationship.properties());
        String graphKey = stringValue(properties.get("graphKey"));
        run("""
                MATCH (source:SourceGraphEntity {uid: $sourceId})
                MATCH (target:SourceGraphEntity {uid: $targetId})
                MERGE (source)-[r:%s {graphKey: $graphKey}]->(target)
                SET r += $properties
                """.formatted(type), Map.of(
                "sourceId", relationship.sourceId(),
                "targetId", relationship.targetId(),
                "graphKey", graphKey,
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
        return mapValue(value, false);
    }

    private Map<String, Object> mapValue(Object value, boolean includeSourceContent) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> target = new LinkedHashMap<>();
            source.forEach((key, mapValue) -> {
                if (key != null && mapValue != null && (includeSourceContent || !"sourceContent".equals(String.valueOf(key)))) {
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
        if (SourceOntology.NODE_LABELS.contains(label)) {
            return label;
        }
        return "JavaType";
    }

    private String classificationLabel(String label, Map<String, Object> properties) {
        if (!"JavaType".equals(label)) {
            return "";
        }
        String layer = stringValue(properties.get("layer"));
        if ("ServiceImpl".equals(layer)) {
            return "Service";
        }
        return SourceOntology.JAVA_TYPE_CLASSIFICATIONS.contains(layer) ? layer : "";
    }

    private String safeRelationshipType(String type) {
        String normalized = StringUtils.hasText(type) ? type.trim().toUpperCase(Locale.ROOT) : "USES";
        if (!SourceOntology.RELATIONSHIP_TYPES.contains(normalized)) {
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
