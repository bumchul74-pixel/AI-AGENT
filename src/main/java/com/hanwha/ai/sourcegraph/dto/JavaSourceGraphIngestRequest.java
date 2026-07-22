package com.hanwha.ai.sourcegraph.dto;

import java.util.List;

public record JavaSourceGraphIngestRequest(
        String source,
        String fileName,
        String content,
        String projectId,
        String moduleName,
        String filePath,
        String fileHash,
        List<String> chunkIds,
        List<String> conformsToRuleIds,
        List<String> violatedRuleIds
) {
    public JavaSourceGraphIngestRequest(String source, String fileName, String content) {
        this(source, fileName, content, null, null, null, null, List.of(), List.of(), List.of());
    }

    public JavaSourceGraphIngestRequest(
            String source,
            String fileName,
            String content,
            String projectId,
            String moduleName,
            String filePath,
            String fileHash
    ) {
        this(source, fileName, content, projectId, moduleName, filePath, fileHash, List.of(), List.of(), List.of());
    }

    public JavaSourceGraphIngestRequest(
            String source,
            String fileName,
            String content,
            String projectId,
            String moduleName,
            String filePath,
            String fileHash,
            List<String> chunkIds
    ) {
        this(source, fileName, content, projectId, moduleName, filePath, fileHash, chunkIds, List.of(), List.of());
    }
}
