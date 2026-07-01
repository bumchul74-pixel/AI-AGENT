package com.hanwha.ai.sourcegraph.service;

import com.hanwha.ai.generation.domain.GenerationHistory;
import com.hanwha.ai.sourcegraph.dto.JavaSourceGraphIngestRequest;
import com.hanwha.ai.sourcegraph.dto.SourceGraphIndexResult;
import com.hanwha.ai.sourcegraph.dto.SourceGraphResponse;
import java.time.LocalDateTime;

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
    public int countJavaSourceFiles() {
        return 0;
    }
}
