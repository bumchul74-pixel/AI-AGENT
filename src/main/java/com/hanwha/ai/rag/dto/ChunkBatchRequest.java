package com.hanwha.ai.rag.dto;

import java.util.List;

public record ChunkBatchRequest(List<String> chunkIds) {
}
