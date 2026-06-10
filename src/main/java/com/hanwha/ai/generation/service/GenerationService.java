package com.hanwha.ai.generation.service;

import com.hanwha.ai.generation.dto.GenerationRequest;
import com.hanwha.ai.generation.dto.GenerationResponse;

public interface GenerationService {
    GenerationResponse generate(GenerationRequest request);
}
