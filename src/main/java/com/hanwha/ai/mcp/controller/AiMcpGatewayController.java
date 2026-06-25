package com.hanwha.ai.mcp.controller;

import com.hanwha.ai.mcp.dto.McpPromptRequest;
import com.hanwha.ai.mcp.dto.McpToolCallRequest;
import com.hanwha.ai.mcp.gateway.AiMcpGatewayService;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "spring.ai.mcp.client", name = "enabled", havingValue = "true")
@RequestMapping("/api/mcp/ai-mcp")
public class AiMcpGatewayController {
    private final AiMcpGatewayService aiMcpGatewayService;

    public AiMcpGatewayController(AiMcpGatewayService aiMcpGatewayService) {
        this.aiMcpGatewayService = aiMcpGatewayService;
    }

    @GetMapping("/ping")
    public Object ping() {
        return aiMcpGatewayService.ping();
    }

    @GetMapping("/info")
    public McpSchema.Implementation serverInfo() {
        return aiMcpGatewayService.serverInfo();
    }

    @GetMapping("/server-info")
    public McpSchema.CallToolResult getServerInfo(
            @RequestParam(defaultValue = "BASIC") String detailLevel
    ) {
        return aiMcpGatewayService.getServerInfo(detailLevel);
    }

    @GetMapping("/tools")
    public McpSchema.ListToolsResult listTools() {
        return aiMcpGatewayService.listTools();
    }

    @PostMapping("/tools/{toolName}")
    public McpSchema.CallToolResult callTool(
            @PathVariable String toolName,
            @RequestBody(required = false) McpToolCallRequest request
    ) {
        return aiMcpGatewayService.callTool(toolName, toolArguments(request));
    }

    @GetMapping("/resources")
    public McpSchema.ListResourcesResult listResources() {
        return aiMcpGatewayService.listResources();
    }

    @GetMapping("/resources/server-info")
    public McpSchema.ReadResourceResult readServerInfoResource() {
        return aiMcpGatewayService.readServerInfoResource();
    }

    @GetMapping("/resources/read")
    public McpSchema.ReadResourceResult readResource(@RequestParam String uri) {
        return aiMcpGatewayService.readResource(uri);
    }

    @GetMapping("/prompts")
    public McpSchema.ListPromptsResult listPrompts() {
        return aiMcpGatewayService.listPrompts();
    }

    @PostMapping("/prompts/{promptName}")
    public McpSchema.GetPromptResult getPrompt(
            @PathVariable String promptName,
            @RequestBody(required = false) McpPromptRequest request
    ) {
        return aiMcpGatewayService.getPrompt(promptName, promptArguments(request));
    }

    private Map<String, Object> toolArguments(McpToolCallRequest request) {
        return request == null || request.arguments() == null ? Map.of() : request.arguments();
    }

    private Map<String, Object> promptArguments(McpPromptRequest request) {
        return request == null || request.arguments() == null ? Map.of() : request.arguments();
    }
}
