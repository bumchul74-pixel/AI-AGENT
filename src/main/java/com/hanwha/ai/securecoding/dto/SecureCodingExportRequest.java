package com.hanwha.ai.securecoding.dto;

import java.util.List;

public record SecureCodingExportRequest(
        String projectKey,
        List<SecureCodingResultRow> rows
) {
}
