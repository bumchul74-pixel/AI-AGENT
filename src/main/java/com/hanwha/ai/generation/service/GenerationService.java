package com.hanwha.ai.generation.service;

import com.hanwha.ai.generation.dto.GenerationHistoryResponse;
import com.hanwha.ai.generation.dto.GenerationHistorySearchRequest;
import com.hanwha.ai.generation.dto.GenerationRequest;
import com.hanwha.ai.generation.dto.GenerationResponse;
import java.util.List;

public interface GenerationService {
    GenerationResponse generate(GenerationRequest request);

    default List<GenerationHistoryResponse> findHistory() {
        return findHistory(GenerationHistorySearchRequest.empty());
    }

    default List<GenerationHistoryResponse> findHistory(GenerationHistorySearchRequest search) {
        throw new UnsupportedOperationException("Generation history lookup is not implemented.");
    }

    default GenerationHistoryResponse findHistoryById(Long id) {
        throw new UnsupportedOperationException("Generation history detail lookup is not implemented.");
    }
}