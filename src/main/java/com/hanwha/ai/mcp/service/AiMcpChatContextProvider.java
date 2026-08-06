package com.hanwha.ai.mcp.service;

import com.hanwha.ai.global.exception.BusinessException;
import com.hanwha.ai.generation.service.ProjectStructureAnalyzer;
import com.hanwha.ai.mcp.gateway.AiMcpGatewayService;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(prefix = "spring.ai.mcp.client", name = "enabled", havingValue = "true")
public class AiMcpChatContextProvider implements McpChatContextProvider {
    private static final String SERVER_INFO_TOOL = "get_server_info";
    private static final String SERVER_INFO_PROMPT = "summarize_server_status";
    private static final String SOURCE_ONTOLOGY_TOOL = "search_source_ontology";
    private static final String LIST_DATABASE_TABLES_TOOL = "list_database_tables";
    private static final List<String> EXPLICIT_TOOL_NAMES = List.of(
            "ocr_document_base64",
            "describe_database_foreign_keys",
            "describe_database_table_columns",
            "generate_mybatis_mapper",
            "describe_database_comments",
            "describe_database_indexes",
            "search_source_ontology",
            "search_database_tables",
            "analyze_project_structure",
            "list_database_tables",
            "ocr_image_base64",
            "ocr_image_file",
            "scan_project",
            "scan_source",
            "scan_file",
            "get_server_info",
            "list_rules"
    );
    private static final Set<String> TABLE_NAME_TOOLS = Set.of(
            "describe_database_comments",
            "describe_database_foreign_keys",
            "describe_database_indexes",
            "describe_database_table_columns",
            "generate_mybatis_mapper"
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

    private final AiMcpGatewayService aiMcpGatewayService;
    private final ProjectStructureAnalyzer projectStructureAnalyzer;

    public AiMcpChatContextProvider(
            AiMcpGatewayService aiMcpGatewayService,
            ProjectStructureAnalyzer projectStructureAnalyzer
    ) {
        this.aiMcpGatewayService = aiMcpGatewayService;
        this.projectStructureAnalyzer = projectStructureAnalyzer;
    }

    @Override
    public boolean supports(String message) {
        String normalized = normalize(message);
        return isJavaSourceRequest(normalized)
                || isProjectStructureAnalysisRequest(message, normalized)
                || normalized.contains("mcp")
                || normalized.contains("server://")
                || normalized.contains(SERVER_INFO_TOOL)
                || explicitToolName(normalized) != null
                || normalized.contains(SERVER_INFO_PROMPT);
    }

    @Override
    public List<String> resolveContext(String message) {
        String normalized = normalize(message);
        McpCallResult result = callMcp(message, normalized);
        return List.of(formatContext(message, result));
    }

    private McpCallResult callMcp(String message, String normalized) {
        String explicitToolName = explicitToolName(normalized);
        if (explicitToolName != null) {
            return callExplicitTool(explicitToolName, message, normalized);
        }

        if (isRuleListRequest(normalized)) {
            return callExplicitTool("list_rules", message, normalized);
        }

        if (isDatabaseTableListRequest(normalized)) {
            return new McpCallResult(
                    "tools/call " + LIST_DATABASE_TABLES_TOOL,
                    aiMcpGatewayService.callTool(LIST_DATABASE_TABLES_TOOL, Map.of())
            );
        }

        if (isJavaSourceRequest(normalized)) {
            return callSourceOntology(message);
        }

        if (isProjectStructureAnalysisRequest(message, normalized)) {
            String projectPath = extractWindowsPath(message);
            String analysis = projectStructureAnalyzer.analyze(projectPath, List.of());
            return new McpCallResult("project-structure/analyze " + projectPath, analysis);
        }

        if (containsAny(normalized, "server://info", "resource read", "read resource")) {
            return new McpCallResult("resources/read server://info", aiMcpGatewayService.readServerInfoResource());
        }

        if (containsAny(normalized, "resources", "resource")) {
            return new McpCallResult("resources/list", aiMcpGatewayService.listResources());
        }

        if (containsAny(normalized, SERVER_INFO_PROMPT)) {
            return new McpCallResult(
                    "prompts/get " + SERVER_INFO_PROMPT,
                    aiMcpGatewayService.getPrompt(SERVER_INFO_PROMPT, Map.of("audience", "developer"))
            );
        }

        if (containsAny(normalized, "prompts", "prompt")) {
            return new McpCallResult("prompts/list", aiMcpGatewayService.listPrompts());
        }

        if (containsAny(normalized, SERVER_INFO_TOOL, "server info")) {
            String detailLevel = detailLevel(normalized);
            return new McpCallResult(
                    "tools/call " + SERVER_INFO_TOOL + " detailLevel=" + detailLevel,
                    aiMcpGatewayService.getServerInfo(detailLevel)
            );
        }

        if (containsAny(normalized, "tools", "tool")) {
            return new McpCallResult("tools/list", aiMcpGatewayService.listTools());
        }

        if (containsAny(normalized, "ping", "status", "health")) {
            return new McpCallResult("ping", aiMcpGatewayService.ping());
        }

        return new McpCallResult("server-info", aiMcpGatewayService.serverInfo());
    }

    private boolean isDatabaseTableListRequest(String normalized) {
        return normalized.contains(LIST_DATABASE_TABLES_TOOL)
                || containsAny(
                normalized,
                "database tables",
                "database table list",
                "table list",
                "tables list",
                "테이블 목록",
                "전체 테이블",
                "테이블과 뷰",
                "테이블 및 뷰"
        );
    }

    private String explicitToolName(String normalized) {
        return EXPLICIT_TOOL_NAMES.stream()
                .filter(toolName -> Pattern.compile(
                        "(?<![a-z0-9_])" + Pattern.quote(toolName) + "(?![a-z0-9_])"
                ).matcher(normalized).find())
                .findFirst()
                .orElse(null);
    }

    private McpCallResult callExplicitTool(String toolName, String message, String normalized) {
        Map<String, Object> arguments = explicitToolArguments(toolName, message, normalized);
        String argumentNames = arguments.isEmpty()
                ? ""
                : " arguments=" + String.join(",", arguments.keySet());
        return new McpCallResult(
                "tools/call " + toolName + argumentNames,
                aiMcpGatewayService.callTool(toolName, arguments)
        );
    }

    private Map<String, Object> explicitToolArguments(
            String toolName,
            String message,
            String normalized
    ) {
        Map<String, Object> arguments = new LinkedHashMap<>();

        if (TABLE_NAME_TOOLS.contains(toolName)) {
            arguments.put("tableName", requiredArgument(
                    toolName,
                    "tableName",
                    extractTableName(message)
            ));
            putOptional(arguments, "schemaName", explicitArgument(message, "schemaName", "schema"));
            if ("generate_mybatis_mapper".equals(toolName)) {
                putOptional(arguments, "domainObjectName", explicitArgument(message, "domainObjectName"));
                putOptional(arguments, "basePackage", explicitArgument(message, "basePackage"));
                putOptional(arguments, "dtoPackage", explicitArgument(message, "dtoPackage"));
                putOptional(arguments, "mapperPackage", explicitArgument(message, "mapperPackage"));
                putOptional(arguments, "operations", explicitArgument(message, "operations"));
            }
            return Map.copyOf(arguments);
        }

        switch (toolName) {
            case "list_rules" -> {
                return Map.of();
            }
            case LIST_DATABASE_TABLES_TOOL ->
                    putOptional(arguments, "schemaName", explicitArgument(message, "schemaName", "schema"));
            case SERVER_INFO_TOOL -> arguments.put("detailLevel", detailLevel(normalized));
            case SOURCE_ONTOLOGY_TOOL -> {
                String query = explicitArgument(message, "query");
                if (!StringUtils.hasText(query)) {
                    query = extractJavaReference(message.replace(SOURCE_ONTOLOGY_TOOL, ""));
                }
                putOptional(arguments, "query", query);
                putOptional(arguments, "projectId", explicitArgument(message, "projectId"));
            }
            case "search_database_tables" -> {
                String keyword = firstText(
                        explicitArgument(message, "keyword", "query", "search"),
                        quotedText(message),
                        extractTableName(message)
                );
                arguments.put("keyword", requiredArgument(toolName, "keyword", keyword));
                putOptional(arguments, "schemaName", explicitArgument(message, "schemaName", "schema"));
            }
            case "analyze_project_structure" -> arguments.put(
                    "projectPath",
                    requiredArgument(toolName, "projectPath", extractPath(message, "projectPath", "path"))
            );
            case "scan_file", "scan_project" -> {
                arguments.put("path", requiredArgument(
                        toolName,
                        "path",
                        extractPath(message, "path", "filePath", "projectPath")
                ));
                putOptionalList(arguments, "ruleSets", explicitArgument(message, "ruleSets"));
            }
            case "scan_source" -> {
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
            case "ocr_image_file" -> arguments.put(
                    "file_path",
                    requiredArgument(toolName, "file_path", extractPath(message, "file_path", "filePath", "path"))
            );
            case "ocr_image_base64" -> arguments.put(
                    "image_base64",
                    requiredArgument(toolName, "image_base64", explicitArgument(message, "image_base64"))
            );
            case "ocr_document_base64" -> {
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
            default -> throw new BusinessException("지원하지 않는 MCP tool입니다: " + toolName);
        }
        return Map.copyOf(arguments);
    }

    private String explicitArgument(String message, String... names) {
        String namePattern = String.join(
                "|",
                List.of(names).stream().map(Pattern::quote).toList()
        );
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

    private boolean isRuleListRequest(String normalized) {
        return containsAny(normalized, "rule list", "rules list", "규칙 목록", "룰 목록");
    }

    private McpCallResult callSourceOntology(String message) {
        String query = extractJavaReference(message);
        Object result = aiMcpGatewayService.callTool(
                SOURCE_ONTOLOGY_TOOL,
                Map.of("query", query)
        );
        return new McpCallResult(
                "tools/call " + SOURCE_ONTOLOGY_TOOL + " query=" + query,
                result
        );
    }

    private String extractJavaReference(String message) {
        if (!StringUtils.hasText(message)) {
            return "";
        }

        Matcher matcher = JAVA_REFERENCE_PATTERN.matcher(message);
        return matcher.results()
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

    private boolean isJavaSourceRequest(String normalized) {
        return containsAny(
                normalized,
                "java",
                "source code",
                "source",
                "controller",
                "repository",
                "mapper",
                "dto",
                "class",
                "method",
                "package",
                "annotation",
                "call graph",
                "dependency",
                "impact analysis",
                "serviceimpl",
                "자바",
                "소스",
                "코드",
                "컨트롤러",
                "리포지토리",
                "레포지토리",
                "매퍼",
                "클래스",
                "메서드",
                "메소드",
                "패키지",
                "어노테이션",
                "호출 관계",
                "호출관계",
                "의존 관계",
                "의존관계",
                "영향도",
                "구현체"
        );
    }

    private boolean isProjectStructureAnalysisRequest(String message, String normalized) {
        return StringUtils.hasText(extractWindowsPath(message))
                && containsAny(
                normalized,
                "project structure",
                "project analysis",
                "analyze project",
                "spring boot",
                "springboot",
                "\ud504\ub85c\uc81d\ud2b8",
                "\uad6c\uc870",
                "\ubd84\uc11d"
        );
    }

    private String extractWindowsPath(String message) {
        if (!StringUtils.hasText(message)) {
            return "";
        }

        Matcher matcher = WINDOWS_PATH_PATTERN.matcher(message);
        return matcher.find() ? trimTrailingPathPunctuation(matcher.group(1)) : "";
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

    private String normalize(String message) {
        return StringUtils.hasText(message) ? message.toLowerCase(Locale.ROOT) : "";
    }

    private String formatContext(String message, McpCallResult result) {
        return """
                MCP user request:
                %s

                MCP gateway operation:
                %s

                MCP gateway result:
                %s
                """.formatted(message, result.operation(), result.result());
    }

    private record McpCallResult(String operation, Object result) {
    }
}
