package com.hanwha.ai.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(
        String apiKey,
        String model,
        String baseUrl,
        Ssl ssl
) {
    public boolean sslTrustAll() {
        return ssl != null && ssl.trustAll();
    }

    public record Ssl(boolean trustAll) {
    }
}
