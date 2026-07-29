package com.hanwha.ai.mcp.service;

import com.hanwha.ai.generation.service.ProjectStructureAnalyzer;
import com.hanwha.ai.mcp.gateway.AiMcpGatewayService;
import java.util.Comparator;
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
                || normalized.contains(SERVER_INFO_PROMPT);
    }

    @Override
    public List<String> resolveContext(String message) {
        String normalized = normalize(message);
        McpCallResult result = callMcp(message, normalized);
        return List.of(formatContext(message, result));
    }

    private McpCallResult callMcp(String message, String normalized) {
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
