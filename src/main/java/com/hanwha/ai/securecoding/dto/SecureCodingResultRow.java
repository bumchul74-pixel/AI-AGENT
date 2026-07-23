package com.hanwha.ai.securecoding.dto;

public record SecureCodingResultRow(
        Long documentId,
        String fileName,
        String fileType,
        String status,
        String severity,
        String ruleId,
        String message,
        Integer startLine,
        Integer startColumn,
        Integer endLine,
        Integer endColumn
) {
}
