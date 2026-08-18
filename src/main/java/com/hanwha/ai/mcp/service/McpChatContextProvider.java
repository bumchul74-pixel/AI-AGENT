package com.hanwha.ai.mcp.service;

import java.util.List;

public interface McpChatContextProvider {
    boolean supports(String message);

    List<String> resolveContext(String message);

    default McpChatContextResult resolve(String message) {
        return new McpChatContextResult(resolveContext(message), null);
    }
}