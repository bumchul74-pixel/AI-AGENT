package com.hanwha.ai.mcp.router;

import com.hanwha.ai.global.exception.BusinessException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DefaultAgentArgumentResolver implements AgentArgumentResolver {
    private static final Set<String> SUPPORTED_RESOLVERS = Set.of(
            "none",
            "database-table-list",
            "database-table",
            "mybatis-mapper",
            "server-info",
            "source-ontology",
            "database-search",
            "project-path",
            "scan-path",
            "scan-source",
            "ocr-image-file",
            "ocr-image-base64",
            "ocr-document-base64"
    );
    private static final Pattern CODE_FENCE_PATTERN = Pattern.compile(
            "(?s)\\x60\\x60\\x60(?:java|sql|xml)?\\s*(.*?)\\x60\\x60\\x60"
    );
    private static final Pattern FILE_NAME_PATTERN = Pattern.compile(
            "(?i)([A-Za-z0-9_.-]+\\.(?:java|sql|xml|pdf|png|jpe?g|bmp|gif|tiff?|webp))"
    );
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile(
            "(?i)((?:[A-Za-z]:\\\\|/)?[A-Za-z0-9_.-]+(?:[\\\\/][A-Za-z0-9_.-]+)+)"
    );
    private static final Pattern TABLE_REFERENCE_PATTERN = Pattern.compile(
            "(?i)([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)?)\\s*(?:테이블|table\\b)"
    );
    private static final Pattern JAVA_REFERENCE_PATTERN = Pattern.compile(
            "(?<![A-Za-z0-9_$])(?:[A-Za-z_$][A-Za-z0-9_$]*\\.)*[A-Za-z_$][A-Za-z0-9_$]*(?:\\.java)?"
                    + "(?![A-Za-z0-9_$])"
    );
    private static final List<String> JAVA_TYPE_SUFFIXES = List.of(
            "Controller", "ServiceImpl", "Service", "Repository", "Mapper", "Dto", "DTO",
            "Entity", "Domain", "Configuration", "Config", "Exception", "Client", "Gateway", "Adapter"
    );
    private static final Set<String> JAVA_REFERENCE_STOP_WORDS = Set.of(
            "api", "class", "code", "dependency", "dto", "field", "java", "mapper", "mcp",
            "method", "package", "repository", "service", "source", "sql", "tool"
    );
    private static final Pattern WINDOWS_PATH_PATTERN = Pattern.compile("(?i)([a-z]:\\\\[^\\s\\\"'<>|]+)");

    @Override
    public boolean supports(String resolverName) {
        return SUPPORTED_RESOLVERS.contains(resolverName);
    }

    @Override
    public Map<String, Object> resolve(
            AgentCapability capability,
            String message,
            String normalizedMessage
    ) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        switch (capability.argumentResolver()) {
            case "none" -> {
                return Map.of();
            }
            case "database-table-list" ->
                    putOptional(arguments, "schemaName", explicitArgument(message, "schemaName", "schema"));
            case "database-table" -> addDatabaseTableArguments(arguments, capability.tool(), message, false);
            case "mybatis-mapper" -> addDatabaseTableArguments(arguments, capability.tool(), message, true);
            case "server-info" -> arguments.put("detailLevel", detailLevel(normalizedMessage));
            case "source-ontology" -> addSourceOntologyArguments(arguments, capability.tool(), message);
            case "database-search" -> addDatabaseSearchArguments(arguments, capability.tool(), message);
            case "project-path" -> arguments.put(
                    "projectPath",
                    requiredArgument(capability.tool(), "projectPath", extractPath(message, "projectPath", "path"))
            );
            case "scan-path" -> addScanPathArguments(arguments, capability.tool(), message);
            case "scan-source" -> addScanSourceArguments(arguments, capability.tool(), message);
            case "ocr-image-file" -> arguments.put(
                    "file_path",
                    requiredArgument(
                            capability.tool(),
                            "file_path",
                            extractPath(message, "file_path", "filePath", "path")
                    )
            );
            case "ocr-image-base64" -> arguments.put(
                    "image_base64",
                    requiredArgument(
                            capability.tool(),
                            "image_base64",
                            explicitArgument(message, "image_base64")
                    )
            );
            case "ocr-document-base64" -> addOcrDocumentArguments(arguments, capability.tool(), message);
            default -> throw new IllegalStateException(
                    "Unsupported agent argument resolver: " + capability.argumentResolver()
            );
        }
        return Map.copyOf(arguments);
    }

    public String extractWindowsPath(String message) {
        if (!StringUtils.hasText(message)) {
            return "";
        }
        Matcher matcher = WINDOWS_PATH_PATTERN.matcher(message);
        return matcher.find() ? trimTrailingPathPunctuation(matcher.group(1)) : "";
    }

    private void addDatabaseTableArguments(
            Map<String, Object> arguments,
            String toolName,
            String message,
            boolean includeMapperOptions
    ) {
        arguments.put("tableName", requiredArgument(toolName, "tableName", extractTableName(message)));
        putOptional(arguments, "schemaName", explicitArgument(message, "schemaName", "schema"));
        if (includeMapperOptions) {
            putOptional(arguments, "domainObjectName", explicitArgument(message, "domainObjectName"));
            putOptional(arguments, "basePackage", explicitArgument(message, "basePackage"));
            putOptional(arguments, "dtoPackage", explicitArgument(message, "dtoPackage"));
            putOptional(arguments, "mapperPackage", explicitArgument(message, "mapperPackage"));
            putOptional(arguments, "operations", explicitArgument(message, "operations"));
        }
    }

    private void addSourceOntologyArguments(
            Map<String, Object> arguments,
            String toolName,
            String message
    ) {
        String query = explicitArgument(message, "query");
        if (!StringUtils.hasText(query)) {
            query = extractJavaReference(message.replace(toolName, ""));
        }
        putOptional(arguments, "query", query);
        putOptional(arguments, "projectId", explicitArgument(message, "projectId"));
    }

    private void addDatabaseSearchArguments(
            Map<String, Object> arguments,
            String toolName,
            String message
    ) {
        String keyword = firstText(
                explicitArgument(message, "keyword", "query", "search"),
                quotedText(message),
                extractTableName(message)
        );
        arguments.put("keyword", requiredArgument(toolName, "keyword", keyword));
        putOptional(arguments, "schemaName", explicitArgument(message, "schemaName", "schema"));
    }

    private void addScanPathArguments(Map<String, Object> arguments, String toolName, String message) {
        arguments.put("path", requiredArgument(
                toolName,
                "path",
                extractPath(message, "path", "filePath", "projectPath")
        ));
        putOptionalList(arguments, "ruleSets", explicitArgument(message, "ruleSets"));
    }

    private void addScanSourceArguments(Map<String, Object> arguments, String toolName, String message) {
        arguments.put("fileName", requiredArgument(
                toolName,
                "fileName",
                firstText(explicitArgument(message, "fileName"), extractFileName(message))
        ));
        arguments.put("source", requiredArgument(
                toolName,
                "source",
                firstText(explicitArgument(message, "source"), extractCodeFence(message))
        ));
        putOptionalList(arguments, "ruleSets", explicitArgument(message, "ruleSets"));
    }

    private void addOcrDocumentArguments(Map<String, Object> arguments, String toolName, String message) {
        arguments.put("file_base64", requiredArgument(
                toolName,
                "file_base64",
                explicitArgument(message, "file_base64")
        ));
        arguments.put("file_name", requiredArgument(
                toolName,
                "file_name",
                firstText(explicitArgument(message, "file_name", "fileName"), extractFileName(message))
        ));
    }

    private String explicitArgument(String message, String... names) {
        String namePattern = String.join("|", List.of(names).stream().map(Pattern::quote).toList());
        Pattern pattern = Pattern.compile(
                "(?is)(?:^|[\\s,{(])(?:" + namePattern + ")\\s*[:=]\\s*"
                        + "(?:\\\"([^\\\"]*)\\\"|'([^']*)'|([^\\s,})]+))"
        );
        Matcher matcher = pattern.matcher(message);
        if (!matcher.find()) {
            return "";
        }
        for (int group = 1; group <= matcher.groupCount(); group++) {
            if (StringUtils.hasText(matcher.group(group))) {
                return matcher.group(group).trim();
            }
        }
        return "";
    }

    private String extractTableName(String message) {
        String explicit = explicitArgument(message, "tableName", "table_name", "table");
        if (StringUtils.hasText(explicit)) {
            return explicit;
        }
        Matcher matcher = TABLE_REFERENCE_PATTERN.matcher(message);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (!Set.of("all", "database").contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
        return "";
    }

    private String extractPath(String message, String... argumentNames) {
        String explicit = explicitArgument(message, argumentNames);
        if (StringUtils.hasText(explicit)) {
            return explicit;
        }
        String windowsPath = extractWindowsPath(message);
        if (StringUtils.hasText(windowsPath)) {
            return windowsPath;
        }
        Matcher matcher = FILE_PATH_PATTERN.matcher(message);
        return matcher.find() ? trimTrailingPathPunctuation(matcher.group(1)) : "";
    }

    private String extractFileName(String message) {
        Matcher matcher = FILE_NAME_PATTERN.matcher(message);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String extractCodeFence(String message) {
        Matcher matcher = CODE_FENCE_PATTERN.matcher(message);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private String quotedText(String message) {
        Matcher matcher = Pattern.compile("[\\\"]([^\\\"]+)[\\\"]|'([^']+)'").matcher(message);
        if (!matcher.find()) {
            return "";
        }
        return StringUtils.hasText(matcher.group(1)) ? matcher.group(1) : matcher.group(2);
    }

    private String extractJavaReference(String message) {
        if (!StringUtils.hasText(message)) {
            return "";
        }
        return JAVA_REFERENCE_PATTERN.matcher(message).results()
                .map(result -> normalizeJavaReference(result.group()))
                .filter(StringUtils::hasText)
                .filter(candidate -> !JAVA_REFERENCE_STOP_WORDS.contains(candidate.toLowerCase(Locale.ROOT)))
                .max(Comparator.comparingInt(this::javaReferenceScore))
                .orElse("");
    }

    private String normalizeJavaReference(String candidate) {
        return candidate.endsWith(".java")
                ? candidate.substring(0, candidate.length() - ".java".length())
                : candidate;
    }

    private int javaReferenceScore(String candidate) {
        String simpleName = candidate.substring(candidate.lastIndexOf('.') + 1);
        int score = candidate.contains(".") ? 300 : 0;
        if (JAVA_TYPE_SUFFIXES.stream().anyMatch(simpleName::endsWith)) {
            score += 200;
        }
        if (Character.isUpperCase(simpleName.charAt(0))) {
            score += 100;
        }
        if (simpleName.substring(1).chars().anyMatch(Character::isUpperCase)) {
            score += 50;
        }
        return score;
    }

    private String firstText(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    private String requiredArgument(String toolName, String argumentName, String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(
                    toolName + " tool 실행에 필요한 " + argumentName + " 값을 입력해 주세요."
            );
        }
        return value;
    }

    private void putOptional(Map<String, Object> arguments, String name, String value) {
        if (StringUtils.hasText(value)) {
            arguments.put(name, value);
        }
    }

    private void putOptionalList(Map<String, Object> arguments, String name, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        arguments.put(
                name,
                Pattern.compile("\\s*[,|]\\s*")
                        .splitAsStream(value)
                        .filter(StringUtils::hasText)
                        .toList()
        );
    }

    private String trimTrailingPathPunctuation(String path) {
        return path.replaceAll("[.,;:]+$", "");
    }

    private String detailLevel(String normalized) {
        return containsAny(normalized, "extended", "detail") ? "EXTENDED" : "BASIC";
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}