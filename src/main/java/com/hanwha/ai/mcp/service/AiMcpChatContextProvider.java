package com.hanwha.ai.mcp.service;

import com.hanwha.ai.generation.service.ProjectStructureAnalyzer;
import com.hanwha.ai.mcp.gateway.AiMcpGatewayService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        return isProjectStructureAnalysisRequest(message, normalized)
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