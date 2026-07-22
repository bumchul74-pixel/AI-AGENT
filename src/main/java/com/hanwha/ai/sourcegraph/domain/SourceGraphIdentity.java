package com.hanwha.ai.sourcegraph.domain;

import java.util.ArrayDeque;
import java.util.Deque;
import org.springframework.util.StringUtils;

public final class SourceGraphIdentity {
    public static final String DEFAULT_PROJECT_ID = "default";
    public static final String DEFAULT_MODULE_NAME = "main";

    private SourceGraphIdentity() {
    }

    public static String projectId(String projectId) {
        return textOrDefault(projectId, DEFAULT_PROJECT_ID);
    }

    public static String moduleName(String moduleName) {
        return textOrDefault(moduleName, DEFAULT_MODULE_NAME);
    }

    public static String normalizeFilePath(String filePath, String fileName) {
        String candidate = textOrDefault(filePath, textOrDefault(fileName, "Source.java"))
                .replace('\\', '/');
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : candidate.split("/+")) {
            if (segment.isBlank() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (!segments.isEmpty()) {
                    segments.removeLast();
                }
                continue;
            }
            segments.addLast(segment);
        }
        return segments.isEmpty() ? "Source.java" : String.join("/", segments);
    }

    public static String sourceFileUid(String projectId, String filePath) {
        return "file:" + projectId(projectId) + ":" + normalizeFilePath(filePath, "Source.java");
    }

    public static String projectUid(String projectId) {
        return "project:" + projectId(projectId);
    }

    public static String moduleUid(String projectId, String moduleName) {
        return "module:" + projectId(projectId) + ":" + moduleName(moduleName);
    }

    public static String packageUid(String projectId, String packageName) {
        return "package:" + projectId(projectId) + ":" + textOrDefault(packageName, "(default)");
    }

    public static String fieldUid(String projectId, String fqn, String fieldName) {
        return "field:" + projectId(projectId) + ":" + fqn.trim() + ":" + fieldName.trim();
    }

    public static String endpointUid(String projectId, String httpMethod, String path) {
        return "endpoint:" + projectId(projectId) + ":" + httpMethod.trim() + ":" + path.trim();
    }

    public static String sqlStatementUid(String projectId, String fqn, String signature) {
        return "statement:" + projectId(projectId) + ":" + fqn.trim() + ":" + signature.trim();
    }

    public static String tableUid(String datasourceId, String schema, String tableName) {
        return "table:" + projectId(datasourceId) + ":" + textOrDefault(schema, "public")
                + ":" + tableName.trim();
    }

    public static String columnUid(String datasourceId, String schema, String tableName, String columnName) {
        return "column:" + projectId(datasourceId) + ":" + textOrDefault(schema, "public")
                + ":" + tableName.trim() + ":" + columnName.trim();
    }

    public static String standardRuleUid(String projectId, String ruleId) {
        return ruleId.startsWith("rule:")
                ? ruleId
                : "rule:" + projectId(projectId) + ":" + ruleId.trim();
    }

    public static String configurationUid(String projectId, String filePath) {
        return "configuration:" + projectId(projectId) + ":" + normalizeFilePath(filePath, "application.yml");
    }

    public static String configurationPropertyUid(String projectId, String key) {
        return "configuration-property:" + projectId(projectId) + ":" + key.trim();
    }

    public static String beanUid(String projectId, String beanName) {
        return "bean:" + projectId(projectId) + ":" + beanName.trim();
    }

    public static String profileUid(String projectId, String profileName) {
        return "profile:" + projectId(projectId) + ":" + profileName.trim();
    }

    public static String providerUid(String projectId, String providerName) {
        return "provider:" + projectId(projectId) + ":" + providerName.trim().toLowerCase();
    }

    public static String typeUid(String projectId, String fqn) {
        return "type:" + projectId(projectId) + ":" + fqn.trim();
    }

    public static String methodUid(String projectId, String fqn, String normalizedSignature) {
        return "method:" + projectId(projectId) + ":" + fqn.trim() + ":" + normalizedSignature.trim();
    }

    private static String textOrDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
