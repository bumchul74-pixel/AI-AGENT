package com.hanwha.ai.rag.service;

import com.hanwha.ai.rag.dto.RagSearchRequest;
import com.hanwha.ai.rag.dto.RagSearchResponse;
import com.hanwha.ai.rag.dto.RagStatsResponse;

public interface RagClient {
    RagSearchResponse search(RagSearchRequest request);

    default RagStatsResponse stats() {
        return RagStatsResponse.empty();
    }
}
