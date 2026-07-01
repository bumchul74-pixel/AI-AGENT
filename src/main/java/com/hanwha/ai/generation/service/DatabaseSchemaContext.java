package com.hanwha.ai.generation.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

public record DatabaseSchemaContext(
        boolean matchedTables,
        String content,
        List<String> tableNames,
        Map<String, List<String>> tableColumns,
        List<String> appliedTools,
        List<String> attemptedTools,
        String statusMessage
) {
    public DatabaseSchemaContext {
        content = content == null ? "" : content;
        tableNames = tableNames == null ? List.of() : List.copyOf(tableNames);
        tableColumns = copyTableColumns(tableColumns);
        appliedTools = appliedTools == null ? List.of() : List.copyOf(appliedTools);
        attemptedTools = attemptedTools == null ? List.of() : List.copyOf(attemptedTools);
        statusMessage = statusMessage == null ? "" : statusMessage;
    }

    public DatabaseSchemaContext(boolean matchedTables, String content) {
        this(matchedTables, content, List.of(), Map.of(), List.of(), List.of(), "");
    }

    public DatabaseSchemaContext(
            boolean matchedTables,
            String content,
            List<String> tableNames,
            Map<String, List<String>> tableColumns
    ) {
        this(matchedTables, content, tableNames, tableColumns, List.of(), List.of(), "");
    }

    public DatabaseSchemaContext(
            boolean matchedTables,
            String content,
            List<String> tableNames,
            Map<String, List<String>> tableColumns,
            List<String> appliedTools
    ) {
        this(matchedTables, content, tableNames, tableColumns, appliedTools, appliedTools, "");
    }

    public static DatabaseSchemaContext empty() {
        return new DatabaseSchemaContext(false, "");
    }

    public boolean hasContent() {
        return StringUtils.hasText(content);
    }

    public boolean hasColumnMetadata() {
        return tableColumns.values().stream().anyMatch(columns -> columns != null && !columns.isEmpty());
    }

    public boolean hasAttemptedTools() {
        return !attemptedTools.isEmpty();
    }

    public boolean hasStatusMessage() {
        return StringUtils.hasText(statusMessage);
    }

    private static Map<String, List<String>> copyTableColumns(Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }

        Map<String, List<String>> copy = new LinkedHashMap<>();
        source.forEach((tableName, columns) -> {
            if (StringUtils.hasText(tableName) && columns != null && !columns.isEmpty()) {
                copy.put(tableName, List.copyOf(columns));
            }
        });
        return Map.copyOf(copy);
    }
}