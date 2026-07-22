package com.hanwha.ai.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hanwha.ai.rag.dto.RagChunkResult;
import com.hanwha.ai.rag.dto.RagSearchRequest;
import com.hanwha.ai.rag.dto.RagSearchResponse;
import com.hanwha.ai.sourcegraph.dto.SourceGraphNodeResponse;
import com.hanwha.ai.sourcegraph.dto.SourceGraphRelationshipResponse;
import com.hanwha.ai.sourcegraph.dto.SourceGraphResponse;
import com.hanwha.ai.sourcegraph.service.SourceGraphService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HybridSearchServiceTest {
    @Test
    void expandsVectorEntitiesThroughGraphAndReloadsEvidenceChunks() {
        RagClient ragClient = mock(RagClient.class);
        SourceGraphService graphService = mock(SourceGraphService.class);
        RagChunkResult vectorChunk = chunk(
                "document:10:chunk:0", "controller source",
                List.of("file:commerce:src/UserController.java"), 0.91
        );
        RagChunkResult evidenceChunk = chunk(
                "document:11:chunk:2", "repository and table evidence", List.of(), 1.0
        );
        SourceGraphResponse graph = new SourceGraphResponse(null, List.of(
                new SourceGraphNodeResponse(
                        "type:commerce:com.example.UserController", "JavaType", "UserController",
                        Map.of("evidenceChunkIds", List.of("document:11:chunk:2"))
                )
        ), List.of(
                new SourceGraphRelationshipResponse(
                        "type:commerce:com.example.UserController",
                        "type:commerce:com.example.UserService",
                        "CALLS",
                        Map.of("evidenceChunkIds", List.of("document:10:chunk:0"))
                )
        ));
        when(ragClient.search(new RagSearchRequest("impact", 5)))
                .thenReturn(new RagSearchResponse(List.of("legacy"), List.of(vectorChunk)));
        when(graphService.findNeighborhoodByEntityIds(
                List.of("file:commerce:src/UserController.java"), 5)).thenReturn(graph);
        when(ragClient.findChunks(anyList())).thenReturn(List.of(vectorChunk, evidenceChunk));

        var result = new HybridSearchService(ragClient, graphService)
                .search(new RagSearchRequest("impact", 5));

        assertThat(result.chunks()).extracting(RagChunkResult::chunkId)
                .containsExactly("document:10:chunk:0", "document:11:chunk:2");
        assertThat(result.context()).contains(
                "GRAPH CONTEXT", "EVIDENCE CHUNKS", "UserController", "repository and table evidence"
        );
        verify(graphService).findNeighborhoodByEntityIds(
                List.of("file:commerce:src/UserController.java"), 5);
        verify(ragClient).findChunks(List.of("document:10:chunk:0", "document:11:chunk:2"));
    }

    private RagChunkResult chunk(String chunkId, String content, List<String> entityIds, double score) {
        return new RagChunkResult(
                chunkId, "document:10", content, entityIds, score, "src/UserController.java", Map.of()
        );
    }
}
