package com.hanwha.ai.mcp.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

@Component
public class AgentConfigurationBootstrapLoader {
    public static final String BOOTSTRAP_VERSION = "bootstrap";

    private final AgentOrchestrationProperties properties;
    private final AgentConfigurationCodec codec;
    private final ResourceLoader resourceLoader;

    public AgentConfigurationBootstrapLoader(
            AgentOrchestrationProperties properties,
            AgentConfigurationCodec codec,
            ResourceLoader resourceLoader
    ) {
        this.properties = properties;
        this.codec = codec;
        this.resourceLoader = resourceLoader;
    }

    public AgentConfigurationDocument load() {
        if (!StringUtils.hasText(properties.getBootstrapLocation())) {
            throw new IllegalStateException("Agent bootstrap location is required.");
        }
        Resource resource = resourceLoader.getResource(properties.getBootstrapLocation());
        try (InputStream input = resource.getInputStream()) {
            return codec.read(StreamUtils.copyToString(input, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Agent bootstrap configuration could not be loaded.", exception);
        }
    }
}