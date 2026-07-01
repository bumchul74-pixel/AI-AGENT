package com.hanwha.ai.generation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanwha.ai.generation.dto.GenerationRequest;
import com.hanwha.ai.mcp.gateway.AiMcpGatewayService;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(prefix = "spring.ai.mcp.client", name = "enabled", havingValue = "true")
public class McpDatabaseSchemaContextProvider implements DatabaseSchemaContextProvider {
    private static final Logger log = LoggerFactory.getLogger(McpDatabaseSchemaContextProvider.class);

    private static final String GENERATE_MYBATIS_MAPPER = "generate_mybatis_mapper";
    private static final String LIST_DATABASE_TABLES = "list_database_tables";
    private static final String SEARCH_DATABASE_TABLES = "search_database_tables";
    private static final String DESCRIBE_DATABASE_TABLE_COLUMNS = "describe_database_table_columns";
    private static final String DESCRIBE_DATABASE_FOREIGN_KEYS = "describe_database_foreign_keys";
    private static final String DESCRIBE_DATABASE_INDEXES = "describe_database_indexes";
    private static final String DESCRIBE_DATABASE_COMMENTS = "describe_database_comments";
    private static final int MAX_TABLES = 3;
    private static final int MAX_TOOL_TEXT_LENGTH = 6_000;
    private static final List<String> SEARCH_ARGUMENT_NAMES = List.of("query", "keyword", "search", "table_name", "tableName", "arg0");
    private static final List<String> TABLE_ARGUMENT_NAMES = List.of("table_name", "tableName", "table", "name", "arg0");
    private static final Set<String> DATABASE_TARGET_TYPES = Set.of("mapper", "dto", "domain");
    private static final Set<String> TABLE_NAME_FIELDS = Set.of("table_name", "tablename", "table", "name");
    private static final Set<String> COLUMN_NAME_FIELDS = Set.of("column_name", "columnname", "column", "field", "name");
    private static final Set<String> IGNORED_TABLE_TOKENS = Set.of(
            "table", "tables", "schema", "database", "public", "name", "type", "comment", "comments", "tb", "tbl", "t", "m"
    );
    private static final Set<String> IGNORED_COLUMN_TOKENS = Set.of(
            "column", "column_name", "columnname", "name", "type", "data_type", "datatype", "nullable", "null",
            "default", "comment", "comments", "key", "pk", "primary", "not", "constraint", "constraints",
            "ordinal_position", "position", "no", "none", "result", "results"
    );
    private static final Pattern LABELED_TABLE_PATTERN = Pattern.compile(
            "(?i)(?:table[_\\s-]?name|table|name)\\s*[:=]\\s*[`\\\"]?([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)?)"
    );

    private final AiMcpGatewayService aiMcpGatewayService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public McpDatabaseSchemaContextProvider(AiMcpGatewayService aiMcpGatewayService) {
        this.aiMcpGatewayService = aiMcpGatewayService;
    }

    @Override
    public DatabaseSchemaContext resolve(GenerationRequest request, List<String> targetTypes) {
        if (!requiresDatabaseContext(targetTypes)) {
            return DatabaseSchemaContext.empty();
        }

        List<String> sections = new ArrayList<>();
        Map<String, List<String>> tableColumns = new LinkedHashMap<>();
        LinkedHashSet<String> appliedTools = new LinkedHashSet<>();
        LinkedHashSet<String> attemptedTools = new LinkedHashSet<>();

        try {
            String prompt = request == null ? "" : request.prompt();
            McpToolText tableList = callTool(LIST_DATABASE_TABLES, Map.of());
            addAttemptedTool(attemptedTools, tableList);
            addAppliedTool(appliedTools, tableList);
            List<String> availableTables = tableList.success() ? extractTableNames(tableList.text()) : List.of();
            List<String> exactPromptTables = exactPromptTables(prompt, availableTables);
            McpToolText tableSearch = callToolWithCompatibleStringArgument(
                    SEARCH_DATABASE_TABLES,
                    prompt,
                    SEARCH_ARGUMENT_NAMES
            );
            addAttemptedTool(attemptedTools, tableSearch);
            addAppliedTool(appliedTools, tableSearch);
            List<String> matchedTables = matchedTables(prompt, availableTables, exactPromptTables, tableSearch);

            sections.add("Source priority: MCP connected database table metadata. Use this before RAG for Mapper, DTO, and DOMAIN content.");
            addToolSection(sections, tableList);
            addToolSection(sections, tableSearch);

            if (matchedTables.isEmpty()) {
                String statusMessage = "No matching database table was found by MCP DB tools.";
                sections.add("Table selection: no matching database table was found from list_database_tables/search_database_tables. RAG fallback must be used for table and field information.");
                return new DatabaseSchemaContext(
                        false,
                        String.join("\n\n", sections),
                        List.of(),
                        Map.of(),
                        List.copyOf(appliedTools),
                        List.copyOf(attemptedTools),
                        statusMessage
                );
            }

            sections.add("Matched database tables: " + String.join(", ", matchedTables));
            if (!exactPromptTables.isEmpty()) {
                sections.add("Table selection: exact table name from user prompt matched list_database_tables. These tables are locked and search_database_tables candidates must not replace them.");
            } else {
                sections.add("Table selection: no exact prompt table match was found, so search_database_tables/list_database_tables matching was used.");
            }

            for (String tableName : matchedTables) {
                sections.add("Database table: " + tableName);
                McpToolText columns = callTableTool(DESCRIBE_DATABASE_TABLE_COLUMNS, tableName);
                addAttemptedTool(attemptedTools, columns);
                addAppliedTool(appliedTools, columns);
                List<String> columnNames = extractColumnNames(columns.text());
                if (!columnNames.isEmpty()) {
                    tableColumns.put(tableName, columnNames);
                    sections.add("Allowed columns for table " + tableName + ": " + String.join(", ", columnNames));
                }
                addToolSection(sections, columns);

                McpToolText foreignKeys = callTableTool(DESCRIBE_DATABASE_FOREIGN_KEYS, tableName);
                addAttemptedTool(attemptedTools, foreignKeys);
                addAppliedTool(appliedTools, foreignKeys);
                addToolSection(sections, foreignKeys);

                McpToolText indexes = callTableTool(DESCRIBE_DATABASE_INDEXES, tableName);
                addAttemptedTool(attemptedTools, indexes);
                addAppliedTool(appliedTools, indexes);
                addToolSection(sections, indexes);

                McpToolText comments = callTableTool(DESCRIBE_DATABASE_COMMENTS, tableName);
                addAttemptedTool(attemptedTools, comments);
                addAppliedTool(appliedTools, comments);
                addToolSection(sections, comments);

                if (containsTargetType(targetTypes, "mapper")) {
                    McpToolText mybatisMapper = callTableTool(GENERATE_MYBATIS_MAPPER, tableName);
                    addAttemptedTool(attemptedTools, mybatisMapper);
                    addAppliedTool(appliedTools, mybatisMapper);
                    addToolSection(sections, mybatisMapper);
                }
            }

            return new DatabaseSchemaContext(
                    true,
                    String.join("\n\n", sections),
                    matchedTables,
                    tableColumns,
                    List.copyOf(appliedTools),
                    List.copyOf(attemptedTools),
                    ""
            );
        } catch (RuntimeException exception) {
            String statusMessage = "MCP database schema context lookup failed: " + exception.getMessage();
            sections.add(statusMessage);
            log.warn("MCP database schema context lookup failed. Falling back to RAG context.", exception);
            return new DatabaseSchemaContext(
                    false,
                    String.join("\n\n", sections),
                    List.of(),
                    tableColumns,
                    List.copyOf(appliedTools),
                    List.copyOf(attemptedTools),
                    statusMessage
            );
        }
    }

    private boolean requiresDatabaseContext(List<String> targetTypes) {
        if (targetTypes == null || targetTypes.isEmpty()) {
            return false;
        }

        return targetTypes.stream()
                .filter(StringUtils::hasText)
                .anyMatch(targetType -> DATABASE_TARGET_TYPES.stream()
                        .anyMatch(expected -> matchesTargetType(targetType, expected)));
    }

    private boolean containsTargetType(List<String> targetTypes, String expected) {
        if (targetTypes == null || !StringUtils.hasText(expected)) {
            return false;
        }

        return targetTypes.stream()
                .filter(StringUtils::hasText)
                .anyMatch(targetType -> matchesTargetType(targetType, expected));
    }

    private boolean matchesTargetType(String targetType, String expected) {
        if (!StringUtils.hasText(targetType) || !StringUtils.hasText(expected)) {
            return false;
        }

        String normalizedTargetType = targetType.trim().toLowerCase(Locale.ROOT);
        String normalizedExpected = expected.trim().toLowerCase(Locale.ROOT);
        return normalizedTargetType.equals(normalizedExpected)
                || normalizedTargetType.contains(normalizedExpected);
    }

    private List<String> matchedTables(
            String prompt,
            List<String> availableTables,
            List<String> exactPromptTables,
            McpToolText tableSearch
    ) {
        if (!exactPromptTables.isEmpty()) {
            return exactPromptTables.stream()
                    .limit(MAX_TABLES)
                    .toList();
        }

        LinkedHashSet<String> tables = new LinkedHashSet<>();
        if (tableSearch.success()) {
            for (String candidate : extractTableNames(tableSearch.text())) {
                String availableTable = matchingAvailableTable(candidate, availableTables);
                if (StringUtils.hasText(availableTable)) {
                    tables.add(availableTable);
                } else if (availableTables.isEmpty()) {
                    tables.add(candidate);
                }
            }
        }

        if (tables.isEmpty() && !availableTables.isEmpty()) {
            availableTables.stream()
                    .filter(tableName -> matchesPrompt(prompt, tableName))
                    .forEach(tables::add);
        }

        return tables.stream()
                .limit(MAX_TABLES)
                .toList();
    }

    private List<String> exactPromptTables(String prompt, List<String> availableTables) {
        if (!StringUtils.hasText(prompt) || availableTables == null || availableTables.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> matches = new LinkedHashSet<>();
        for (String tableName : availableTables) {
            if (promptContainsIdentifier(prompt, tableName) || promptContainsIdentifier(prompt, simpleTableName(tableName))) {
                matches.add(tableName);
            }
        }
        return matches.stream()
                .limit(MAX_TABLES)
                .toList();
    }

    private boolean promptContainsIdentifier(String prompt, String identifier) {
        if (!StringUtils.hasText(prompt) || !StringUtils.hasText(identifier)) {
            return false;
        }

        return Pattern.compile(
                "(?i)(?<![A-Za-z0-9_])" + Pattern.quote(identifier.trim()) + "(?![A-Za-z0-9_])"
        ).matcher(prompt).find();
    }

    private String matchingAvailableTable(String candidate, List<String> availableTables) {
        if (!StringUtils.hasText(candidate) || availableTables == null || availableTables.isEmpty()) {
            return "";
        }

        return availableTables.stream()
                .filter(availableTable -> sameTableName(candidate, availableTable))
                .findFirst()
                .orElse("");
    }

    private boolean sameTableName(String left, String right) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }

        String normalizedLeft = left.trim().toLowerCase(Locale.ROOT);
        String normalizedRight = right.trim().toLowerCase(Locale.ROOT);
        return normalizedLeft.equals(normalizedRight)
                || simpleTableName(normalizedLeft).equals(simpleTableName(normalizedRight));
    }

    private boolean matchesPrompt(String prompt, String tableName) {
        if (!StringUtils.hasText(prompt) || !StringUtils.hasText(tableName)) {
            return false;
        }

        String normalizedPrompt = prompt.toLowerCase(Locale.ROOT);
        String simpleName = simpleTableName(tableName).toLowerCase(Locale.ROOT);
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(simpleName);
        candidates.add(simpleName.replace("_", ""));
        candidates.add(singular(simpleName));
        candidates.add(singular(simpleName.replace("_", "")));

        for (String token : simpleName.split("_")) {
            if (token.length() >= 3 && !IGNORED_TABLE_TOKENS.contains(token)) {
                candidates.add(token);
                candidates.add(singular(token));
            }
        }

        return candidates.stream()
                .filter(StringUtils::hasText)
                .anyMatch(normalizedPrompt::contains);
    }

    private String simpleTableName(String tableName) {
        if (!StringUtils.hasText(tableName)) {
            return "";
        }
        String normalized = tableName.trim();
        return normalized.substring(normalized.lastIndexOf('.') + 1);
    }

    private String singular(String value) {
        if (!StringUtils.hasText(value) || value.length() <= 3 || !value.endsWith("s")) {
            return value;
        }
        return value.substring(0, value.length() - 1);
    }

    private McpToolText callTableTool(String toolName, String tableName) {
        return callToolWithCompatibleStringArgument(toolName, tableName, TABLE_ARGUMENT_NAMES);
    }

    private McpToolText callToolWithCompatibleStringArgument(String toolName, String value, List<String> argumentNames) {
        McpToolText firstFailure = null;
        for (String argumentName : argumentNames) {
            McpToolText result = callTool(toolName, Map.of(argumentName, StringUtils.hasText(value) ? value.trim() : ""));
            if (result.success()) {
                return result;
            }
            if (firstFailure == null) {
                firstFailure = result;
            }
        }

        return firstFailure == null ? McpToolText.failed(toolName, Map.of(), "MCP tool call failed.") : firstFailure;
    }

    private McpToolText callTool(String toolName, Map<String, Object> arguments) {
        try {
            McpSchema.CallToolResult result = aiMcpGatewayService.callTool(toolName, arguments);
            boolean success = result != null && !Boolean.TRUE.equals(result.isError());
            return new McpToolText(toolName, arguments, success, formatToolResult(result));
        } catch (RuntimeException exception) {
            log.debug("MCP tool call failed. toolName={}", toolName, exception);
            return McpToolText.failed(toolName, arguments, exception.getMessage());
        }
    }

    private String formatToolResult(McpSchema.CallToolResult result) {
        if (result == null) {
            return "";
        }

        List<McpSchema.Content> contents = result.content() == null ? List.of() : result.content();
        String contentText = contents.stream()
                .map(this::formatContent)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n"));
        String structuredText = structuredContentText(result.structuredContent());

        if (StringUtils.hasText(contentText) && StringUtils.hasText(structuredText)) {
            return contentText + "\n" + structuredText;
        }
        if (StringUtils.hasText(contentText)) {
            return contentText;
        }
        if (StringUtils.hasText(structuredText)) {
            return structuredText;
        }
        return result.toString();
    }

    private String formatContent(McpSchema.Content content) {
        if (content instanceof McpSchema.TextContent textContent) {
            return textContent.text();
        }
        return content == null ? "" : content.toString();
    }

    private String structuredContentText(Object structuredContent) {
        if (structuredContent == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(structuredContent);
        } catch (JsonProcessingException exception) {
            return String.valueOf(structuredContent);
        }
    }

    private void addToolSection(List<String> sections, McpToolText result) {
        if (result == null || !result.hasText()) {
            return;
        }

        sections.add("""
                Tool: %s
                Arguments: %s
                Status: %s
                Result:
                %s
                """.formatted(
                result.toolName(),
                result.arguments(),
                result.success() ? "success" : "failed",
                abbreviate(result.text(), MAX_TOOL_TEXT_LENGTH)
        ).trim());
    }

    private void addAppliedTool(LinkedHashSet<String> appliedTools, McpToolText result) {
        if (result != null && result.success()) {
            appliedTools.add(result.toolName());
        }
    }

    private void addAttemptedTool(LinkedHashSet<String> attemptedTools, McpToolText result) {
        if (result != null) {
            attemptedTools.add(result.toolName());
        }
    }

    private List<String> extractTableNames(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }

        LinkedHashSet<String> tableNames = new LinkedHashSet<>();
        collectJsonTableNames(text, tableNames);
        collectLabeledTableNames(text, tableNames);
        collectLineTableNames(text, tableNames);
        return tableNames.stream()
                .filter(this::isTableIdentifier)
                .filter(tableName -> !IGNORED_TABLE_TOKENS.contains(tableName.toLowerCase(Locale.ROOT)))
                .limit(MAX_TABLES * 2L)
                .toList();
    }

    private List<String> extractColumnNames(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }

        LinkedHashSet<String> columnNames = new LinkedHashSet<>();
        collectJsonColumnNames(text, columnNames);
        collectLineColumnNames(text, columnNames);
        return columnNames.stream()
                .filter(this::isColumnIdentifier)
                .filter(columnName -> !IGNORED_COLUMN_TOKENS.contains(columnName.toLowerCase(Locale.ROOT)))
                .toList();
    }

    private void collectJsonTableNames(String text, LinkedHashSet<String> tableNames) {
        try {
            collectJsonTableNames(objectMapper.readTree(text), tableNames);
        } catch (JsonProcessingException exception) {
            log.debug("MCP database tool result is not JSON.", exception);
        }
    }

    private void collectJsonTableNames(JsonNode node, LinkedHashSet<String> tableNames) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            addTableName(tableNames, node.asText());
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectJsonTableNames(child, tableNames));
            return;
        }
        if (!node.isObject()) {
            return;
        }

        String tableName = textField(node, "table_name", "tableName", "tablename", "table", "name");
        if (StringUtils.hasText(tableName)) {
            addTableName(tableNames, tableName);
        }

        node.fields().forEachRemaining(entry -> collectJsonTableNames(entry.getValue(), tableNames));
    }

    private void collectJsonColumnNames(String text, LinkedHashSet<String> columnNames) {
        try {
            collectJsonColumnNames(objectMapper.readTree(text), columnNames);
        } catch (JsonProcessingException exception) {
            log.debug("MCP database column result is not JSON.", exception);
        }
    }

    private void collectJsonColumnNames(JsonNode node, LinkedHashSet<String> columnNames) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectJsonColumnNames(child, columnNames));
            return;
        }
        if (!node.isObject()) {
            return;
        }

        String columnName = textField(node, "column_name", "columnName", "column", "field", "name");
        if (StringUtils.hasText(columnName)) {
            addColumnName(columnNames, columnName);
        }

        node.fields().forEachRemaining(entry -> collectJsonColumnNames(entry.getValue(), columnNames));
    }

    private String textField(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && value.isTextual() && StringUtils.hasText(value.asText())) {
                return value.asText();
            }
        }
        return "";
    }

    private void collectLabeledTableNames(String text, LinkedHashSet<String> tableNames) {
        Matcher matcher = LABELED_TABLE_PATTERN.matcher(text);
        while (matcher.find()) {
            addTableName(tableNames, matcher.group(1));
        }
    }

    private void collectLineTableNames(String text, LinkedHashSet<String> tableNames) {
        for (String line : text.split("\\R")) {
            String normalized = line.trim();
            if (!StringUtils.hasText(normalized)) {
                continue;
            }

            if (normalized.contains("|")) {
                for (String cell : normalized.split("\\|")) {
                    addTableName(tableNames, cell);
                }
                continue;
            }

            addTableName(tableNames, normalized);
            addLeadingTableName(tableNames, normalized, ":");
            addLeadingTableName(tableNames, normalized, " - ");
        }
    }

    private void collectLineColumnNames(String text, LinkedHashSet<String> columnNames) {
        for (String line : text.split("\\R")) {
            String normalized = line.trim();
            if (!StringUtils.hasText(normalized)) {
                continue;
            }

            if (normalized.contains("|")) {
                addColumnName(columnNames, normalized.split("\\|")[0]);
                continue;
            }

            addColumnName(columnNames, normalized);
        }
    }

    private void addTableName(LinkedHashSet<String> tableNames, String candidate) {
        String tableName = cleanTableName(candidate);
        if (isTableIdentifier(tableName)) {
            tableNames.add(tableName);
        }
    }

    private void addLeadingTableName(LinkedHashSet<String> tableNames, String line, String delimiter) {
        int delimiterIndex = line.indexOf(delimiter);
        if (delimiterIndex > 0) {
            addTableName(tableNames, line.substring(0, delimiterIndex));
        }
    }

    private void addColumnName(LinkedHashSet<String> columnNames, String candidate) {
        String columnName = cleanColumnName(candidate);
        if (isColumnIdentifier(columnName)) {
            columnNames.add(columnName);
        }
    }

    private String cleanTableName(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return "";
        }

        String cleaned = candidate.trim()
                .replace("`", "")
                .replace("\"", "")
                .replace("'", "")
                .replace("[", "")
                .replace("]", "")
                .replace("(", "")
                .replace(")", "");
        cleaned = cleaned.replaceFirst("^[-*\\s]+", "");
        cleaned = cleaned.replaceFirst("[,:;]+$", "");
        return cleaned.trim();
    }

    private String cleanColumnName(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return "";
        }

        String cleaned = candidate.trim()
                .replace("`", "")
                .replace("\"", "")
                .replace("'", "")
                .replace("[", "")
                .replace("]", "")
                .replace("(", "")
                .replace(")", "");
        cleaned = cleaned.replaceFirst("^[-*\\s]+", "");
        int colonIndex = cleaned.indexOf(':');
        if (colonIndex > 0) {
            cleaned = cleaned.substring(0, colonIndex);
        }
        String[] tokens = cleaned.trim().split("\\s+");
        cleaned = tokens.length == 0 ? "" : tokens[0];
        cleaned = cleaned.replaceFirst("[,:;]+$", "");
        if (cleaned.contains(".")) {
            cleaned = cleaned.substring(cleaned.lastIndexOf('.') + 1);
        }
        return cleaned.trim();
    }

    private boolean isTableIdentifier(String value) {
        return StringUtils.hasText(value)
                && value.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)?")
                && !TABLE_NAME_FIELDS.contains(value.toLowerCase(Locale.ROOT));
    }

    private boolean isColumnIdentifier(String value) {
        return StringUtils.hasText(value)
                && value.matches("[A-Za-z_][A-Za-z0-9_]*")
                && !COLUMN_NAME_FIELDS.contains(value.toLowerCase(Locale.ROOT));
    }

    private String abbreviate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "\n... truncated ...";
    }

    private record McpToolText(String toolName, Map<String, Object> arguments, boolean success, String text) {
        private static McpToolText failed(String toolName, Map<String, Object> arguments, String message) {
            return new McpToolText(toolName, arguments, false, message == null ? "" : message);
        }

        private boolean hasText() {
            return StringUtils.hasText(text);
        }
    }
}