package com.hanwha.ai.sourcegraph.service;

import com.hanwha.ai.generation.domain.GenerationHistory;
import com.hanwha.ai.sourcegraph.dto.JavaSourceGraphIngestRequest;
import com.hanwha.ai.sourcegraph.dto.SourceGraphIndexResult;
import com.hanwha.ai.sourcegraph.dto.SourceGraphNodeSourceResponse;
import com.hanwha.ai.sourcegraph.dto.SourceGraphResponse;
import com.hanwha.ai.sourcegraph.dto.SourceGraphSourceResponse;
import java.time.LocalDateTime;
import java.util.List;

public final class NoOpSourceGraphService implements SourceGraphService {
    public static final NoOpSourceGraphService INSTANCE = new NoOpSourceGraphService();

    private NoOpSourceGraphService() {
    }

    @Override
    public SourceGraphIndexResult index(GenerationHistory history) {
        return SourceGraphIndexResult.skipped(LocalDateTime.now(), "Source graph indexing is not configured.");
    }

    @Override
    public SourceGraphIndexResult indexJavaSource(JavaSourceGraphIngestRequest request) {
        return SourceGraphIndexResult.skipped(LocalDateTime.now(), "Source graph indexing is not configured.");
    }

    @Override
    public SourceGraphResponse findOverview(String query, int limit) {
        return SourceGraphResponse.empty(null);
    }

    @Override
    public SourceGraphResponse findByHistoryId(Long historyId) {
        return SourceGraphResponse.empty(historyId);
    }

    @Override
    public SourceGraphResponse findDependencies(String fqn) {
        return SourceGraphResponse.empty(null);
    }

    @Override
    public SourceGraphResponse findImpacts(String fqn) {
        return SourceGraphResponse.empty(null);
    }

    @Override
    public SourceGraphResponse findNeighborhoodByEntityIds(List<String> entityIds, int depth) {
        return SourceGraphResponse.empty(null);
    }

    @Override
    public SourceGraphNodeSourceResponse findNodeSource(String nodeId) {
        return SourceGraphNodeSourceResponse.unavailable(nodeId, "Source graph indexing is not configured.");
    }

    @Override
    public void deleteBySourceKey(String sourceKey) {
        // Nothing to delete when source graph indexing is disabled.
    }

    @Override
    public void deleteByGraphKey(String graphKey) {
        // Nothing to delete when source graph indexing is disabled.
    }

    @Override
    public List<SourceGraphSourceResponse> findIndexedSources() {
        return List.of();
    }

    @Override
    public int countJavaSourceFiles() {
        return 0;
    }
}
