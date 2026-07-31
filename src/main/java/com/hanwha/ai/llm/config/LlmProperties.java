package com.hanwha.ai.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm")
public class LlmProperties {
    private String provider;
    private boolean fallbackEnabled;
    private String fallbackProvider;

    public LlmProperties() {
    }

    public LlmProperties(String provider) {
        this.provider = provider;
        this.fallbackEnabled = false;
        this.fallbackProvider = "";
    }

    public LlmProperties(String provider, boolean fallbackEnabled, String fallbackProvider) {
        this.provider = provider;
        this.fallbackEnabled = fallbackEnabled;
        this.fallbackProvider = fallbackProvider;
    }

    public String provider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean fallbackEnabled() {
        return fallbackEnabled;
    }

    public void setFallbackEnabled(boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }

    public String fallbackProvider() {
        return fallbackProvider;
    }

    public void setFallbackProvider(String fallbackProvider) {
        this.fallbackProvider = fallbackProvider;
    }
}