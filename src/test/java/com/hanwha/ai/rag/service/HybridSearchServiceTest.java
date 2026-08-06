package com.hanwha.ai.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hanwha.ai.rag.config.RagProperties;
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
    void expandsOnlyTheSelectedProjectAndReloadsLimitedEvidenceChunks() {
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
                        Map.of(
                                "projectId", "commerce",
                                "evidenceChunkIds", List.of("document:11:chunk:2")
                        )
                ),
                new SourceGraphNodeResponse(
                        "type:commerce:com.example.UserService", "JavaType", "UserService",
                        Map.of("projectId", "commerce")
                )
        ), List.of(
                new SourceGraphRelationshipResponse(
                        "type:commerce:com.example.UserController",
                        "type:commerce:com.example.UserService",
                        "CALLS",
                        Map.of("evidenceChunkIds", List.of("document:10:chunk:0"))
                )
        ));
        RagSearchRequest request = new RagSearchRequest("impact", 5, "commerce");
        when(ragClient.search(request))
                .thenReturn(new RagSearchResponse(List.of("legacy"), List.of(vectorChunk)));
        when(graphService.findNeighborhoodByEntityIds(
                List.of("file:commerce:src/UserController.java"), 2, "commerce", 50, 200))
                .thenReturn(graph);
        when(ragClient.findChunks(List.of(
                "document:10:chunk:0", "document:11:chunk:2")))
                .thenReturn(List.of(vectorChunk, evidenceChunk));

        var result = new HybridSearchService(
                ragClient,
                graphService,
                new RagProperties("", "", 5)
        ).search(request);

        assertThat(result.chunks()).extracting(RagChunkResult::chunkId)
                .containsExactly("document:10:chunk:0", "document:11:chunk:2");
        assertThat(result.context()).contains(
                "EVIDENCE CHUNKS", "GRAPH CONTEXT", "UserController", "repository and table evidence"
        );
        verify(graphService).findNeighborhoodByEntityIds(
                List.of("file:commerce:src/UserController.java"), 2, "commerce", 50, 200);
        verify(ragClient).findChunks(List.of(
                "document:10:chunk:0", "document:11:chunk:2"));
    }

    @Test
    void capsGraphEvidenceAndFinalContextSize() {
        RagClient ragClient = mock(RagClient.class);
        SourceGraphService graphService = mock(SourceGraphService.class);
        RagChunkResult vectorChunk = chunk(
                "vector", "v".repeat(100),
                List.of("type:commerce:User"), 0.99
        );
        RagChunkResult evidenceChunk = chunk(
                "evidence-1", "e".repeat(100), List.of(), 1.0
        );
        SourceGraphResponse graph = new SourceGraphResponse(null, List.of(
                new SourceGraphNodeResponse(
                        "type:commerce:User", "JavaType", "User",
                        Map.of(
                                "projectId", "commerce",
                                "evidenceChunkIds", List.of("evidence-1", "evidence-2")
                        )
                ),
                new SourceGraphNodeResponse(
                        "type:other:User", "JavaType", "OtherUser",
                        Map.of("projectId", "other")
                )
        ), List.of());
        RagSearchRequest request = new RagSearchRequest("User CRUD", 5, "commerce");
        RagProperties properties = new RagProperties("", "", 5, 1, 1, 1, 2, 140);
        when(ragClient.search(request))
                .thenReturn(new RagSearchResponse(List.of(), List.of(vectorChunk)));
        when(graphService.findNeighborhoodByEntityIds(
                List.of("type:commerce:User"), 1, "commerce", 1, 1))
                .thenReturn(graph);
        when(ragClient.findChunks(List.of("vector", "evidence-1")))
                .thenReturn(List.of(vectorChunk, evidenceChunk));

        var result = new HybridSearchService(ragClient, graphService, properties).search(request);

        assertThat(result.graph().nodes()).extracting(SourceGraphNodeResponse::id)
                .containsExactly("type:commerce:User");
        assertThat(result.chunks()).extracting(RagChunkResult::chunkId)
                .containsExactly("vector", "evidence-1");
        assertThat(result.context()).hasSize(140)
                .startsWith("EVIDENCE CHUNKS")
                .endsWith("[Context truncated to configured character limit]");
    }

    @Test
    void respectsContextLimitsShorterThanTheTruncationMarker() {
        RagClient ragClient = mock(RagClient.class);
        SourceGraphService graphService = mock(SourceGraphService.class);
        RagChunkResult vectorChunk = chunk("vector", "content", List.of(), 0.99);
        RagSearchRequest request = new RagSearchRequest("User CRUD", 5, "commerce");
        when(ragClient.search(request))
                .thenReturn(new RagSearchResponse(List.of(), List.of(vectorChunk)));
        when(ragClient.findChunks(List.of("vector"))).thenReturn(List.of(vectorChunk));

        var result = new HybridSearchService(
                ragClient,
                graphService,
                new RagProperties("", "", 5, 1, 1, 1, 1, 10)
        ).search(request);

        assertThat(result.context()).hasSize(10).isEqualTo("EVIDENCE C");
    }

    private RagChunkResult chunk(String chunkId, String content, List<String> entityIds, double score) {
        return new RagChunkResult(
                chunkId, "document:10", content, entityIds, score, "src/UserController.java", Map.of()
        );
    }
}
