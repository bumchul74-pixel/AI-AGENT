package com.hanwha.ai.mcp.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(properties = {
        "spring.ai.mcp.client.enabled=true",
        "spring.ai.mcp.client.initialized=false"
})
class McpLazyInitializationContextTest {
    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoadsWithoutEagerMcpInitialization() {
        assertThat(applicationContext).isNotNull();
    }
}