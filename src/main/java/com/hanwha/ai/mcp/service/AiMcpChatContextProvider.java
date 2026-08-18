package com.hanwha.ai.mcp.service;

import com.hanwha.ai.mcp.orchestration.AgentExecutionResult;
import com.hanwha.ai.mcp.orchestration.AgentExecutionView;
import com.hanwha.ai.mcp.orchestration.AgentOrchestrator;
import com.hanwha.ai.mcp.orchestration.AgentStepExecution;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "spring.ai.mcp.client", name = "enabled", havingValue = "true")
public class AiMcpChatContextProvider implements McpChatContextProvider {
    private final AgentOrchestrator agentOrchestrator;

    public AiMcpChatContextProvider(AgentOrchestrator agentOrchestrator) {
        this.agentOrchestrator = agentOrchestrator;
    }

    @Override
    public boolean supports(String message) {
        return agentOrchestrator.supports(message);
    }

    @Override
    public McpChatContextResult resolve(String message) {
        AgentExecutionResult execution = agentOrchestrator.execute(message);
        List<String> contexts = execution.steps().stream()
                .filter(AgentStepExecution::successful)
                .map(step -> formatContext(message, step.route().operation(), step.result()))
                .toList();
        return new McpChatContextResult(contexts, AgentExecutionView.from(execution));
    }

    @Override
    public List<String> resolveContext(String message) {
        return resolve(message).contexts();
    }

    private String formatContext(String message, String operation, Object result) {
        return """
                MCP user request:
                %s

                MCP gateway operation:
                %s

                MCP gateway result:
                %s
                """.formatted(message, operation, result);
    }
}
