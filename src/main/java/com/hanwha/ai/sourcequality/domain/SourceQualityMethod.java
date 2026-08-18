package com.hanwha.ai.sourcequality.domain;

public record SourceQualityMethod(
        String methodUid,
        String declaringType,
        String signature,
        String filePath,
        int startLine,
        int endLine,
        int lineCount,
        String methodHash,
        String structuralHash,
        int cyclomaticComplexity,
        int cognitiveComplexity,
        int maxNestingDepth,
        int parameterCount,
        int returnCount,
        int throwCount,
        int branchCount,
        int callCount
) {
}
