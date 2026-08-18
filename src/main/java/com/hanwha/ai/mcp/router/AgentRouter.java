package com.hanwha.ai.mcp.router;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AgentRouter {
    private static final String RULE_LIST_CAPABILITY = "security.rule-list";
    private static final String DATABASE_TABLE_LIST_CAPABILITY = "database.table-list";
    private static final String SOURCE_SEARCH_CAPABILITY = "source.search";
    private static final String SERVER_INFO_CAPABILITY = "server.info";
    private static final String SERVER_INFO_PROMPT = "summarize_server_status";
    private static final String SERVER_INFO_RESOURCE = "server://info";
    private static final Pattern WINDOWS_PATH_PATTERN = Pattern.compile("(?i)([a-z]:\\\\[^\\s\\\"'<>|]+)");

    private final AgentRegistry registry;
    private final List<AgentArgumentResolver> argumentResolvers;

    public AgentRouter(AgentRegistry registry, List<AgentArgumentResolver> argumentResolvers) {
        this.registry = registry;
        this.argumentResolvers = List.copyOf(argumentResolvers);
        validateCapabilities();
    }

    private void validateCapabilities() {
        for (AgentCapability capability : registry.capabilities()) {
            if (!"mcp".equals(capability.executor()) || !"ai-mcp".equals(capability.server())) {
                throw new IllegalStateException(
                        "Unsupported capability execution target: " + capability.id()
                );
            }
            if (argumentResolvers.stream()
                    .noneMatch(resolver -> resolver.supports(capability.argumentResolver()))) {
                throw new IllegalStateException(
                        "No AgentArgumentResolver for capability " + capability.id()
                                + ": " + capability.argumentResolver()
                );
            }
        }
    }

    public boolean supports(String message) {
        return supports(message, registry.snapshot());
    }

    public boolean supports(String message, AgentRegistrySnapshot snapshot) {
        String normalized = normalize(message);
        return isJavaSourceRequest(normalized)
                || isProjectStructureAnalysisRequest(message, normalized)
                || normalized.contains("mcp")
                || normalized.contains("server://")
                || snapshot.findMentionedTool(normalized).isPresent()
                || normalized.contains(SERVER_INFO_PROMPT);
    }

    public AgentRoute route(String message) {
        return route(message, registry.snapshot());
    }

    public AgentRoute route(String message, AgentRegistrySnapshot snapshot) {
        String normalized = normalize(message);
        AgentCapability explicitCapability = snapshot.findMentionedTool(normalized).orElse(null);
        if (explicitCapability != null) {
            return explicitCapabilityRoute(explicitCapability, message, normalized);
        }

        if (isRuleListRequest(normalized)) {
            return explicitCapabilityRoute(
                    snapshot.requiredCapability(RULE_LIST_CAPABILITY),
                    message,
                    normalized
            );
        }

        if (isDatabaseTableListRequest(normalized)) {
            AgentCapability capability = snapshot.requiredCapability(DATABASE_TABLE_LIST_CAPABILITY);
            return capabilityRoute(capability, resolveArguments(capability, message, normalized));
        }

        if (isJavaSourceRequest(normalized)) {
            return sourceSearchRoute(message, normalized, snapshot);
        }

        if (isProjectStructureAnalysisRequest(message, normalized)) {
            String projectPath = extractWindowsPath(message);
            return route(
                    AgentRoute.Kind.PROJECT_STRUCTURE_ANALYSIS,
                    "project-structure/analyze " + projectPath,
                    projectPath,
                    Map.of()
            );
        }

        if (containsAny(normalized, "server://info", "resource read", "read resource")) {
            return route(
                    AgentRoute.Kind.RESOURCE_READ,
                    "resources/read " + SERVER_INFO_RESOURCE,
                    SERVER_INFO_RESOURCE,
                    Map.of()
            );
        }

        if (containsAny(normalized, "resources", "resource")) {
            return route(AgentRoute.Kind.RESOURCE_LIST, "resources/list", "", Map.of());
        }

        if (containsAny(normalized, SERVER_INFO_PROMPT)) {
            return route(
                    AgentRoute.Kind.PROMPT_GET,
                    "prompts/get " + SERVER_INFO_PROMPT,
                    SERVER_INFO_PROMPT,
                    Map.of("audience", "developer")
            );
        }

        if (containsAny(normalized, "prompts", "prompt")) {
            return route(AgentRoute.Kind.PROMPT_LIST, "prompts/list", "", Map.of());
        }

        if (containsAny(normalized, "server info")) {
            AgentCapability capability = snapshot.requiredCapability(SERVER_INFO_CAPABILITY);
            Map<String, Object> arguments = resolveArguments(capability, message, normalized);
            String detailLevel = String.valueOf(arguments.get("detailLevel"));
            return capabilityRoute(
                    capability,
                    arguments,
                    "tools/call " + capability.tool() + " detailLevel=" + detailLevel
            );
        }

        if (containsAny(normalized, "tools", "tool")) {
            return route(AgentRoute.Kind.TOOL_LIST, "tools/list", "", Map.of());
        }

        if (containsAny(normalized, "ping", "status", "health")) {
            return route(AgentRoute.Kind.PING, "ping", "", Map.of());
        }

        return route(AgentRoute.Kind.SERVER_INFO, "server-info", "", Map.of());
    }

    public AgentRoute routeCapability(String capabilityId, String message) {
        return routeCapability(capabilityId, message, registry.snapshot());
    }

    public AgentRoute routeCapability(
            String capabilityId,
            String message,
            AgentRegistrySnapshot snapshot
    ) {
        String normalized = normalize(message);
        return explicitCapabilityRoute(snapshot.requiredCapability(capabilityId), message, normalized);
    }

    private AgentRoute explicitCapabilityRoute(
            AgentCapability capability,
            String message,
            String normalized
    ) {
        return capabilityRoute(capability, resolveArguments(capability, message, normalized));
    }

    private AgentRoute sourceSearchRoute(
            String message,
            String normalized,
            AgentRegistrySnapshot snapshot
    ) {
        AgentCapability capability = snapshot.requiredCapability(SOURCE_SEARCH_CAPABILITY);
        Map<String, Object> arguments = resolveArguments(capability, message, normalized);
        String query = String.valueOf(arguments.getOrDefault("query", ""));
        return capabilityRoute(
                capability,
                arguments,
                "tools/call " + capability.tool() + " query=" + query
        );
    }

    private AgentRoute capabilityRoute(AgentCapability capability, Map<String, Object> arguments) {
        String argumentNames = arguments.isEmpty()
                ? ""
                : " arguments=" + String.join(",", arguments.keySet());
        return capabilityRoute(
                capability,
                arguments,
                "tools/call " + capability.tool() + argumentNames
        );
    }

    private AgentRoute capabilityRoute(
            AgentCapability capability,
            Map<String, Object> arguments,
            String operation
    ) {
        return route(AgentRoute.Kind.TOOL_CALL, operation, capability.tool(), arguments);
    }

    private Map<String, Object> resolveArguments(
            AgentCapability capability,
            String message,
            String normalized
    ) {
        return argumentResolvers.stream()
                .filter(resolver -> resolver.supports(capability.argumentResolver()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No AgentArgumentResolver for capability " + capability.id()
                                + ": " + capability.argumentResolver()
                ))
                .resolve(capability, message, normalized);
    }

    private AgentRoute route(
            AgentRoute.Kind kind,
            String operation,
            String target,
            Map<String, Object> arguments
    ) {
        return new AgentRoute(kind, operation, target, arguments);
    }

    private boolean isDatabaseTableListRequest(String normalized) {
        return containsAny(
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

    private boolean isRuleListRequest(String normalized) {
        return containsAny(normalized, "rule list", "rules list", "규칙 목록", "룰 목록");
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
                "프로젝트",
                "구조",
                "분석"
        );
    }

    private String extractWindowsPath(String message) {
        if (!StringUtils.hasText(message)) {
            return "";
        }
        Matcher matcher = WINDOWS_PATH_PATTERN.matcher(message);
        return matcher.find() ? matcher.group(1).replaceAll("[.,;:]+$", "") : "";
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
}