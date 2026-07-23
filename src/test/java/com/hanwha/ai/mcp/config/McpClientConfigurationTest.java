package com.hanwha.ai.mcp.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest(properties = "spring.ai.mcp.client.enabled=false")
class McpClientConfigurationTest {
    @Autowired
    private Environment environment;

    @Test
    void loadsMcpClientConfiguration() {
        assertThat(environment.getProperty("spring.ai.mcp.client.name"))
                .isEqualTo("ai-agent-mcp-client");
        assertThat(environment.getProperty("spring.ai.mcp.client.type"))
                .isEqualTo("SYNC");
        assertThat(environment.getProperty("spring.ai.mcp.client.initialized", Boolean.class))
                .isFalse();
        assertThat(environment.getProperty("spring.ai.mcp.client.streamable-http.connections.ai-mcp.url"))
                .isEqualTo("http://localhost:8092");
        assertThat(environment.getProperty("spring.ai.mcp.client.streamable-http.connections.ai-mcp.endpoint"))
                .isEqualTo("/mcp");
        assertThat(environment.getProperty("mcp.filesystem.root"))
                .isEqualTo("d:\\workspace\\AI-AGENT");
        assertThat(environment.getProperty(
                "spring.ai.mcp.client.stdio.connections.server-filesystem.command"))
                .isNull();
    }

    @Test
    void allowsMcpConfigurationToBeOverridden() {
        assertThat(environment.getProperty("spring.ai.mcp.client.enabled", Boolean.class))
                .isFalse();
    }

    @Test
    void findsSpecificJavaFileFromConfiguredFilesystemRoot() throws IOException {
        Path filesystemRoot = Path.of(environment.getRequiredProperty(
                "mcp.filesystem.root"
        ));

        try (var paths = Files.find(
                filesystemRoot,
                10,
                (path, attributes) -> attributes.isRegularFile()
                        && path.endsWith(Path.of("src", "main", "java", "com", "hanwha", "ai", "AiAgentApplication.java"))
        )) {
            assertThat(paths.toList())
                    .singleElement()
                    .satisfies(path -> assertThat(path.getFileName().toString())
                            .isEqualTo("AiAgentApplication.java"));
        }
    }
}
