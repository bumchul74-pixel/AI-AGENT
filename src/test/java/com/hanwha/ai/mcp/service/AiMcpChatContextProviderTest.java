package com.hanwha.ai.mcp.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanwha.ai.generation.service.ProjectStructureAnalyzer;
import com.hanwha.ai.mcp.gateway.AiMcpGatewayService;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AiMcpChatContextProviderTest {
    @Test
    void routesLocalProjectStructureRequestToProjectStructureAnalyzer() {
        AtomicReference<String> analyzedPath = new AtomicReference<>();
        ProjectStructureAnalyzer projectStructureAnalyzer = (projectPath, targetTypes) -> {
            analyzedPath.set(projectPath);
            return """
                    Local project structure analysis:
                    Project full path: %s
                    Spring Boot version: 3.2.0
                    Java version: 21
                    """.formatted(projectPath);
        };
        AiMcpChatContextProvider provider = new AiMcpChatContextProvider(
                new AiMcpGatewayService(null),
                projectStructureAnalyzer
        );
        String message = "\ub85c\uceec D:\\workspace\\management \uc758 \ud504\ub85c\uc81d\ud2b8 \uad6c\uc870\ub97c \ubd84\uc11d\ud574\uc918";

        assertThat(provider.supports(message)).isTrue();

        List<String> contexts = provider.resolveContext(message);

        assertThat(analyzedPath.get()).isEqualTo("D:\\workspace\\management");
        assertThat(contexts).hasSize(1);
        assertThat(contexts.get(0)).contains(
                "MCP gateway operation:",
                "project-structure/analyze D:\\workspace\\management",
                "Spring Boot version: 3.2.0",
                "Java version: 21"
        );
    }
}