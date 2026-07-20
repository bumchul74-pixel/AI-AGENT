package com.hanwha.ai.sourcegraph.dto;

public record SourceGraphNodeSourceResponse(
        String nodeId,
        String label,
        String name,
        String fqn,
        String fileName,
        String sourceKind,
        String graphSourceKey,
        String filePath,
        String content,
        boolean available,
        String message
) {
    public static SourceGraphNodeSourceResponse available(
            String nodeId,
            String label,
            String name,
            String fqn,
            String fileName,
            String sourceKind,
            String content
    ) {
        return available(nodeId, label, name, fqn, fileName, sourceKind, "", "", content);
    }

    public static SourceGraphNodeSourceResponse available(
            String nodeId,
            String label,
            String name,
            String fqn,
            String fileName,
            String sourceKind,
            String graphSourceKey,
            String filePath,
            String content
    ) {
        return new SourceGraphNodeSourceResponse(
                nodeId,
                label,
                name,
                fqn,
                fileName,
                sourceKind,
                graphSourceKey,
                filePath,
                content,
                true,
                ""
        );
    }

    public static SourceGraphNodeSourceResponse unavailable(String nodeId, String message) {
        return new SourceGraphNodeSourceResponse(nodeId, "", "", "", "", "", "", "", "", false, message);
    }
}