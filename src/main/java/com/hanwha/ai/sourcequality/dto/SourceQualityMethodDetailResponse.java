package com.hanwha.ai.sourcequality.dto;

import com.hanwha.ai.sourcequality.domain.SourceQualityMethodDetail;

public record SourceQualityMethodDetailResponse(
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
        int callCount,
        String methodBody
) {
    public static SourceQualityMethodDetailResponse from(SourceQualityMethodDetail detail) {
        var method = detail.method();
        return new SourceQualityMethodDetailResponse(
                method.methodUid(), method.declaringType(), method.signature(), method.filePath(),
                method.startLine(), method.endLine(), method.lineCount(),
                method.cyclomaticComplexity(), method.cognitiveComplexity(), method.maxNestingDepth(),
                method.parameterCount(), method.returnCount(), method.throwCount(),
                method.branchCount(), method.callCount(), detail.methodBody());
    }
}
