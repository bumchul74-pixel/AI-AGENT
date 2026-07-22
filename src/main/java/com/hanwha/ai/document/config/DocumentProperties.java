package com.hanwha.ai.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "document")
public record DocumentProperties(
        String storageDirectory,
        int chunkSize,
        int overlap,
        String projectId,
        String moduleName
) {
    public DocumentProperties {
        if (storageDirectory == null || storageDirectory.isBlank()) {
            storageDirectory = "uploads/documents";
        }
        if (chunkSize <= 0) {
            chunkSize = 1200;
        }
        if (overlap < 0) {
            overlap = 150;
        }
        if (projectId == null || projectId.isBlank()) {
            projectId = "default";
        }
        if (moduleName == null || moduleName.isBlank()) {
            moduleName = "main";
        }
    }
}
