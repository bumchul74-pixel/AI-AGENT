package com.hanwha.ai.rag.service;

import com.hanwha.ai.rag.dto.RagSearchRequest;
import com.hanwha.ai.rag.dto.RagSearchResponse;

public interface RagClient {
    RagSearchResponse search(RagSearchRequest request);
}
