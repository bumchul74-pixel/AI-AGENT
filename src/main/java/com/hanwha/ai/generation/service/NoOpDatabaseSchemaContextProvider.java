package com.hanwha.ai.generation.service;

import com.hanwha.ai.generation.dto.GenerationRequest;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "spring.ai.mcp.client", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpDatabaseSchemaContextProvider implements DatabaseSchemaContextProvider {
    public static final NoOpDatabaseSchemaContextProvider INSTANCE = new NoOpDatabaseSchemaContextProvider();

    @Override
    public DatabaseSchemaContext resolve(GenerationRequest request, List<String> targetTypes) {
        return DatabaseSchemaContext.empty();
    }
}