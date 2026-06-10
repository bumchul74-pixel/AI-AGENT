package com.hanwha.ai.llm.service;

import com.hanwha.ai.llm.domain.LlmProvider;
import com.hanwha.ai.llm.dto.LlmGenerateRequest;
import com.hanwha.ai.llm.dto.LlmGenerateResponse;

public interface LlmClient {
    LlmProvider provider();

    LlmGenerateResponse generate(LlmGenerateRequest request);
}
