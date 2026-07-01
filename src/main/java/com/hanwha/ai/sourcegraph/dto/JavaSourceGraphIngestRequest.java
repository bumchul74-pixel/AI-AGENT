package com.hanwha.ai.sourcegraph.dto;

public record JavaSourceGraphIngestRequest(
        String source,
        String fileName,
        String content
) {
}