package com.hanwha.ai.sourcegraph.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "source-graph")
public record SourceGraphProperties(boolean enabled) {
}