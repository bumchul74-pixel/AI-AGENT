package com.hanwha.ai.mcp.service;

import com.hanwha.ai.mcp.orchestration.AgentExecutionView;
import java.util.List;

public record McpChatContextResult(
        List<String> contexts,
        AgentExecutionView execution
) {
    public McpChatContextResult {
        contexts = contexts == null ? List.of() : List.copyOf(contexts);
    }
}