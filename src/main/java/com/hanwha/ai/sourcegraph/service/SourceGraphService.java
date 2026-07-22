package com.hanwha.ai.sourcegraph.service;

import com.hanwha.ai.generation.domain.GenerationHistory;
import com.hanwha.ai.sourcegraph.dto.JavaSourceGraphIngestRequest;
import com.hanwha.ai.sourcegraph.dto.SourceGraphIndexResult;
import com.hanwha.ai.sourcegraph.dto.SourceGraphNodeSourceResponse;
import com.hanwha.ai.sourcegraph.dto.SourceGraphResponse;
import com.hanwha.ai.sourcegraph.dto.SourceGraphSourceResponse;
import java.util.List;

public interface SourceGraphService {
    SourceGraphIndexResult index(GenerationHistory history);

    SourceGraphIndexResult indexJavaSource(JavaSourceGraphIngestRequest request);

    SourceGraphResponse findOverview(String query, int limit);

    SourceGraphResponse findByHistoryId(Long historyId);

    SourceGraphResponse findDependencies(String fqn);

    SourceGraphResponse findImpacts(String fqn);

    SourceGraphResponse findNeighborhoodByEntityIds(List<String> entityIds, int depth);

    SourceGraphNodeSourceResponse findNodeSource(String nodeId);

    void deleteBySourceKey(String sourceKey);

    void deleteByGraphKey(String graphKey);

    List<SourceGraphSourceResponse> findIndexedSources();

    int countJavaSourceFiles();
}
