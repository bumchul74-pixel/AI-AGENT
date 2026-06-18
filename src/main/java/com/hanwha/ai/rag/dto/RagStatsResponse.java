package com.hanwha.ai.rag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RagStatsResponse(
        @JsonProperty("java_file_count") int javaFileCount
) {
    public static RagStatsResponse empty() {
        return new RagStatsResponse(0);
    }
}
