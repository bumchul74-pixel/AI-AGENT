package com.hanwha.ai.sourcegraph.dto;

import java.util.List;

public record SourceGraphResponse(
        Long historyId,
        List<SourceGraphNodeResponse> nodes,
        List<SourceGraphRelationshipResponse> relationships
) {
    public static SourceGraphResponse empty(Long historyId) {
        return new SourceGraphResponse(historyId, List.of(), List.of());
    }
}