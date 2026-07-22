package com.hanwha.ai.rag.service;

import com.hanwha.ai.rag.dto.RagSearchRequest;
import com.hanwha.ai.rag.dto.RagSearchResponse;
import com.hanwha.ai.rag.dto.RagStatsResponse;
import com.hanwha.ai.rag.dto.RagChunkResult;
import com.hanwha.ai.rag.dto.RagSourceResponse;
import java.util.List;

public interface RagClient {
    RagSearchResponse search(RagSearchRequest request);

    default List<RagChunkResult> findChunks(List<String> chunkIds) {
        return List.of();
    }

    default RagStatsResponse stats() {
        return RagStatsResponse.empty();
    }

    default List<RagSourceResponse> findSources() {
        return List.of();
    }

    default int deleteSource(String sourceKey) {
        return 0;
    }
}
