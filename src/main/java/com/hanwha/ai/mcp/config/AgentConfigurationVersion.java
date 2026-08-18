package com.hanwha.ai.mcp.config;

import java.time.LocalDateTime;

public record AgentConfigurationVersion(
        Long id,
        String versionKey,
        String status,
        int maxParallelism,
        String configurationJson,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime activatedAt
) {
}