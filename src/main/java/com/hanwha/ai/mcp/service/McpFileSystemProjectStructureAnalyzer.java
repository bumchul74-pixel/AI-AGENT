package com.hanwha.ai.mcp.service;

import com.hanwha.ai.generation.service.ProjectStructureAnalyzer;
import com.hanwha.ai.global.exception.BusinessException;
import com.hanwha.ai.mcp.gateway.AiMcpGatewayService;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(prefix = "spring.ai.mcp.client", name = "enabled", havingValue = "true")
public class McpFileSystemProjectStructureAnalyzer implements ProjectStructureAnalyzer {
    private static final String TOOL_NAME = "analyze_project_structure";

    private final AiMcpGatewayService aiMcpGatewayService;

    public McpFileSystemProjectStructureAnalyzer(AiMcpGatewayService aiMcpGatewayService) {
        this.aiMcpGatewayService = aiMcpGatewayService;
    }

    @Override
    public String analyze(String projectPath, List<String> targetTypes) {
        if (!StringUtils.hasText(projectPath)) {
            throw new BusinessException("projectStructure must contain a project full path.");
        }

        String selectedProjectPath = projectPath.trim();
        McpSchema.CallToolResult result;
        try {
            result = callAnalyzerWithCompatibleArgumentName(selectedProjectPath);
        } catch (RuntimeException exception) {
            throw new BusinessException("MCP project structure analysis failed.", exception);
        }
        if (result == null) {
            throw new BusinessException("MCP project structure analysis returned empty result.");
        }

        String analysis = formatToolResult(result);
        if (Boolean.TRUE.equals(result.isError())) {
            throw new BusinessException("MCP project structure analysis failed: " + analysis);
        }

        return """
                MCP project structure analysis:
                Tool: %s
                Project full path: %s
                Selected target types: %s

                Analysis result:
                %s
                """.formatted(
                TOOL_NAME,
                selectedProjectPath,
                targetTypesText(targetTypes),
                analysis
        );
    }

    private String targetTypesText(List<String> targetTypes) {
        return targetTypes == null || targetTypes.isEmpty() ? "none" : String.join(", ", targetTypes);
    }

    private McpSchema.CallToolResult callAnalyzerWithCompatibleArgumentName(String projectPath) {
        RuntimeException firstFailure = null;
        for (String argumentName : List.of("project_path", "projectPath", "arg0", "path")) {
            try {
                McpSchema.CallToolResult result = callAnalyzer(projectPath, argumentName);
                if (!Boolean.TRUE.equals(result.isError())) {
                    return result;
                }
            } catch (RuntimeException exception) {
                if (firstFailure == null) {
                    firstFailure = exception;
                } else {
                    exception.addSuppressed(firstFailure);
                    firstFailure = exception;
                }
            }
        }

        throw firstFailure;
    }

    private McpSchema.CallToolResult callAnalyzer(String projectPath, String argumentName) {
        return aiMcpGatewayService.callTool(TOOL_NAME, Map.of(argumentName, projectPath));
    }

    private String formatToolResult(McpSchema.CallToolResult result) {
        List<McpSchema.Content> contents = result.content() == null ? List.of() : result.content();
        String contentText = contents.stream()
                .map(this::formatContent)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n"));
        if (StringUtils.hasText(contentText)) {
            return contentText;
        }

        if (result.structuredContent() != null) {
            return result.structuredContent().toString();
        }

        return result.toString();
    }

    private String formatContent(McpSchema.Content content) {
        if (content instanceof McpSchema.TextContent textContent) {
            return textContent.text();
        }
        return content.toString();
    }
}