package com.hanwha.ai.rag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class HybridSearchService {
    private static final int GRAPH_DEPTH = 5;
    private final RagClient ragClient;
    private final SourceGraphService sourceGraphService;
    private final ObjectMapper objectMapper;

    public HybridSearchService(
            RagClient ragClient,
            SourceGraphService sourceGraphService
    ) {
        this.ragClient = ragClient;
        this.sourceGraphService = sourceGraphService;
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
        SourceGraphResponse graph = graphNeighborhood(entityIds);
        Set<String> evidenceChunkIds = evidenceChunkIds(vectorChunks, graph);
        List<RagChunkResult> evidenceChunks = ragClient.findChunks(List.copyOf(evidenceChunkIds));
        List<RagChunkResult> mergedChunks = mergeChunks(vectorChunks, evidenceChunks);
        List<String> documents = mergedChunks.stream().map(this::formatChunk).toList();
        return new HybridSearchResult(documents, mergedChunks, graph, buildContext(graph, mergedChunks));
    }

    private SourceGraphResponse graphNeighborhood(List<String> entityIds) {
        if (entityIds.isEmpty()) {
            return SourceGraphResponse.empty(null);
        }
        try {
            SourceGraphResponse graph = sourceGraphService.findNeighborhoodByEntityIds(entityIds, GRAPH_DEPTH);
            return graph == null ? SourceGraphResponse.empty(null) : graph;
        } catch (RuntimeException exception) {
            return SourceGraphResponse.empty(null);
        }
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
        if (graph != null && (!graph.nodes().isEmpty() || !graph.relationships().isEmpty())) {
            sections.add("GRAPH CONTEXT:\n" + graphJson(graph));
        }
        if (!chunks.isEmpty()) {
            sections.add("EVIDENCE CHUNKS:\n" + String.join("\n\n", chunks.stream()
                    .map(this::formatChunk)
                    .toList()));
        }
        return String.join("\n\n", sections);
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
