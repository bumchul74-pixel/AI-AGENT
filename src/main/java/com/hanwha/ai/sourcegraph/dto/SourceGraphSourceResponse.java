package com.hanwha.ai.sourcegraph.dto;

public record SourceGraphSourceResponse(
        String sourceKey,
        String graphKey,
        String projectId,
        String fileName,
        int nodeCount
) {
}
