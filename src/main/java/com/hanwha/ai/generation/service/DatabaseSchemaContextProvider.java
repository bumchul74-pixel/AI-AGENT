package com.hanwha.ai.generation.service;

import com.hanwha.ai.generation.dto.GenerationRequest;
import java.util.List;

public interface DatabaseSchemaContextProvider {
    DatabaseSchemaContext resolve(GenerationRequest request, List<String> targetTypes);
}