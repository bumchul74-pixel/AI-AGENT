package com.hanwha.ai.rag.dto;

import com.hanwha.ai.sourcegraph.dto.SourceGraphResponse;
import java.util.List;

public record HybridSearchResult(
        List<String> documents,
        List<RagChunkResult> chunks,
        SourceGraphResponse graph,
        String context
) {
}
