package com.hanwha.ai.mcp.service;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "spring.ai.mcp.client", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpMcpChatContextProvider implements McpChatContextProvider {
    @Override
    public boolean supports(String message) {
        return false;
    }

    @Override
    public List<String> resolveContext(String message) {
        return List.of();
    }
}