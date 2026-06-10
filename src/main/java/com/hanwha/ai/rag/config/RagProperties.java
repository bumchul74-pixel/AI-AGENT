package com.hanwha.ai.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag")
public record RagProperties(
        String baseUrl,
        String searchPath,
        int topK
) {
}
