package com.hanwha.ai.sourcequality.dto;

import com.hanwha.ai.sourcequality.domain.SourceQualityMethod;

public record SourceQualityMethodResponse(
        String methodUid,
        String declaringType,
        String signature,
        String filePath,
        int startLine,
        int endLine,
        int lineCount,
        int cyclomaticComplexity,
        int cognitiveComplexity,
        int maxNestingDepth,
        int parameterCount,
        int returnCount,
        int throwCount,
        int branchCount,
        int callCount
) {
    public static SourceQualityMethodResponse from(SourceQualityMethod method) {
        return new SourceQualityMethodResponse(
                method.methodUid(), method.declaringType(), method.signature(), method.filePath(),
                method.startLine(), method.endLine(), method.lineCount(),
                method.cyclomaticComplexity(), method.cognitiveComplexity(),
                method.maxNestingDepth(), method.parameterCount(), method.returnCount(),
                method.throwCount(), method.branchCount(), method.callCount());
    }
}
