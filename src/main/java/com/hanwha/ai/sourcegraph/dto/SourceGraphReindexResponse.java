package com.hanwha.ai.sourcegraph.dto;

public record SourceGraphReindexResponse(
        SourceGraphIndexResult indexResult,
        SourceGraphResponse graph
) {
}