package com.hanwha.ai.rag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanwha.ai.rag.config.RagProperties;
import com.hanwha.ai.rag.dto.HybridSearchResult;
import com.hanwha.ai.rag.dto.RagChunkResult;
import com.hanwha.ai.rag.dto.RagSearchRequest;
import com.hanwha.ai.rag.dto.RagSearchResponse;
import com.hanwha.ai.sourcegraph.dto.SourceGraphNodeResponse;
import com.hanwha.ai.sourcegraph.dto.SourceGraphRelationshipResponse;
import com.hanwha.ai.sourcegraph.dto.SourceGraphResponse;
import com.hanwha.ai.sourcegraph.service.SourceGraphService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class HybridSearchService {
    private static final Logger log = LoggerFactory.getLogger(HybridSearchService.class);
    private static final String TRUNCATION_MARKER = "\n\n[Context truncated to configured character limit]";

    private final RagClient ragClient;
    private final SourceGraphService sourceGraphService;
    private final RagProperties properties;
    private final ObjectMapper objectMapper;

    public HybridSearchService(
            RagClient ragClient,
            SourceGraphService sourceGraphService
    ) {
        this(ragClient, sourceGraphService, new RagProperties("", "", 5));
    }

    @Autowired
    public HybridSearchService(
            RagClient ragClient,
            SourceGraphService sourceGraphService,
            RagProperties properties
    ) {
        this.ragClient = ragClient;
        this.sourceGraphService = sourceGraphService;
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
    }

    public HybridSearchResult search(RagSearchRequest request) {
        RagSearchResponse vectorResult = ragClient.search(request);
        List<RagChunkResult> vectorChunks = vectorResult == null ? List.of() : vectorResult.chunks();
        if (vectorChunks.isEmpty()) {
            List<String> documents = vectorResult == null ? List.of() : vectorResult.documents();
            return new HybridSearchResult(
                    documents, List.of(), SourceGraphResponse.empty(null), String.join("\n", documents)
            );
        }

        List<String> entityIds = vectorChunks.stream()
                .map(RagChunkResult::entityIds)
                .flatMap(Collection::stream)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        SourceGraphResponse graph = graphNeighborhood(entityIds, request.projectId());
        List<String> evidenceChunkIds = evidenceChunkIds(vectorChunks, graph).stream()
                .limit(properties.hybridMaxEvidenceChunks())
                .toList();
        List<RagChunkResult> evidenceChunks = ragClient.findChunks(evidenceChunkIds);
        List<RagChunkResult> mergedChunks = mergeChunks(vectorChunks, evidenceChunks).stream()
                .limit(properties.hybridMaxEvidenceChunks())
                .toList();
        List<String> documents = mergedChunks.stream().map(this::formatChunk).toList();
        String context = buildContext(graph, mergedChunks);
        log.debug(
                "Built hybrid RAG context. projectId={} vectorChunks={} evidenceChunks={} graphNodes={} "
                        + "graphRelationships={} contextLength={}",
                request.projectId(),
                vectorChunks.size(),
                mergedChunks.size(),
                graph.nodes().size(),
                graph.relationships().size(),
                context.length()
        );
        return new HybridSearchResult(documents, mergedChunks, graph, context);
    }

    private SourceGraphResponse graphNeighborhood(List<String> entityIds, String projectId) {
        if (entityIds.isEmpty()) {
            return SourceGraphResponse.empty(null);
        }
        try {
            SourceGraphResponse graph = sourceGraphService.findNeighborhoodByEntityIds(
                    entityIds,
                    properties.hybridGraphDepth(),
                    projectId,
                    properties.hybridMaxGraphNodes(),
                    properties.hybridMaxGraphRelationships()
            );
            return limitGraph(graph, projectId);
        } catch (RuntimeException exception) {
            log.debug("Source graph neighborhood lookup failed.", exception);
            return SourceGraphResponse.empty(null);
        }
    }

    private SourceGraphResponse limitGraph(SourceGraphResponse graph, String projectId) {
        if (graph == null) {
            return SourceGraphResponse.empty(null);
        }
        List<SourceGraphNodeResponse> nodes = graph.nodes().stream()
                .filter(node -> matchesProject(node, projectId))
                .limit(properties.hybridMaxGraphNodes())
                .toList();
        Set<String> nodeIds = new HashSet<>(nodes.stream().map(SourceGraphNodeResponse::id).toList());
        List<SourceGraphRelationshipResponse> relationships = graph.relationships().stream()
                .filter(relationship -> nodeIds.contains(relationship.sourceId())
                        && nodeIds.contains(relationship.targetId()))
                .limit(properties.hybridMaxGraphRelationships())
                .toList();
        return new SourceGraphResponse(graph.historyId(), nodes, relationships);
    }

    private boolean matchesProject(SourceGraphNodeResponse node, String projectId) {
        if (!StringUtils.hasText(projectId)) {
            return true;
        }
        Object nodeProjectId = node.properties() == null ? null : node.properties().get("projectId");
        return projectId.trim().equals(String.valueOf(nodeProjectId));
    }

    private Set<String> evidenceChunkIds(List<RagChunkResult> vectorChunks, SourceGraphResponse graph) {
        Set<String> ids = new LinkedHashSet<>();
        vectorChunks.stream().map(RagChunkResult::chunkId).filter(StringUtils::hasText).forEach(ids::add);
        if (graph != null) {
            graph.nodes().forEach(node -> addEvidenceIds(ids, node.properties()));
            graph.relationships().forEach(relationship -> addEvidenceIds(ids, relationship.properties()));
        }
        return ids;
    }

    private void addEvidenceIds(Set<String> target, Map<String, Object> properties) {
        if (properties == null) {
            return;
        }
        Object value = properties.get("evidenceChunkIds");
        if (value instanceof Collection<?> collection) {
            collection.stream().map(String::valueOf).filter(StringUtils::hasText).forEach(target::add);
        } else if (value instanceof String text && StringUtils.hasText(text)) {
            target.add(text);
        }
    }

    private List<RagChunkResult> mergeChunks(
            List<RagChunkResult> vectorChunks,
            List<RagChunkResult> evidenceChunks
    ) {
        Map<String, RagChunkResult> chunks = new LinkedHashMap<>();
        vectorChunks.forEach(chunk -> chunks.put(chunk.chunkId(), chunk));
        evidenceChunks.forEach(chunk -> chunks.putIfAbsent(chunk.chunkId(), chunk));
        return List.copyOf(chunks.values());
    }

    private String buildContext(SourceGraphResponse graph, List<RagChunkResult> chunks) {
        List<String> sections = new ArrayList<>();
        if (!chunks.isEmpty()) {
            sections.add("EVIDENCE CHUNKS:\n" + String.join("\n\n", chunks.stream()
                    .map(this::formatChunk)
                    .toList()));
        }
        if (graph != null && (!graph.nodes().isEmpty() || !graph.relationships().isEmpty())) {
            sections.add("GRAPH CONTEXT:\n" + graphJson(graph));
        }
        return truncateContext(String.join("\n\n", sections));
    }

    private String truncateContext(String context) {
        int limit = properties.maxContextCharacters();
        if (context.length() <= limit) {
            return context;
        }
        if (limit <= TRUNCATION_MARKER.length()) {
            return context.substring(0, limit);
        }
        int contentLimit = limit - TRUNCATION_MARKER.length();
        return context.substring(0, contentLimit) + TRUNCATION_MARKER;
    }

    private String graphJson(SourceGraphResponse graph) {
        try {
            return objectMapper.writeValueAsString(graph);
        } catch (JsonProcessingException exception) {
            String nodes = graph.nodes().stream().map(SourceGraphNodeResponse::id).toList().toString();
            String relationships = graph.relationships().stream()
                    .map(this::formatRelationship).toList().toString();
            return "nodes=" + nodes + ", relationships=" + relationships;
        }
    }

    private String formatRelationship(SourceGraphRelationshipResponse relationship) {
        return relationship.sourceId() + " -[" + relationship.type() + "]-> " + relationship.targetId();
    }

    private String formatChunk(RagChunkResult chunk) {
        return "[chunkId: " + chunk.chunkId() + "][source: " + chunk.sourceKey() + "]\n" + chunk.content();
    }
}
