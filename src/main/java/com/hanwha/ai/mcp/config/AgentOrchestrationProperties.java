package com.hanwha.ai.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.orchestration.config")
public class AgentOrchestrationProperties {
    private boolean databaseEnabled = true;
    private boolean seedEnabled = true;
    private boolean fallbackOnStartup = true;
    private RefreshMode refreshMode = RefreshMode.POLLING;
    private long refreshIntervalMs = 30_000;
    private String bootstrapLocation = "classpath:agent-orchestration-bootstrap.json";
    private boolean adminApiEnabled = true;
    private boolean toolSyncEnabled = true;
    private long toolSyncIntervalMs = 30_000;
    private long toolSyncInitialDelayMs = 30_000;

    public boolean isDatabaseEnabled() { return databaseEnabled; }
    public void setDatabaseEnabled(boolean databaseEnabled) { this.databaseEnabled = databaseEnabled; }
    public boolean isSeedEnabled() { return seedEnabled; }
    public void setSeedEnabled(boolean seedEnabled) { this.seedEnabled = seedEnabled; }
    public boolean isFallbackOnStartup() { return fallbackOnStartup; }
    public void setFallbackOnStartup(boolean fallbackOnStartup) {
        this.fallbackOnStartup = fallbackOnStartup;
    }
    public RefreshMode getRefreshMode() { return refreshMode; }
    public void setRefreshMode(RefreshMode refreshMode) {
        this.refreshMode = refreshMode == null ? RefreshMode.POLLING : refreshMode;
    }
    public long getRefreshIntervalMs() { return refreshIntervalMs; }
    public void setRefreshIntervalMs(long refreshIntervalMs) {
        this.refreshIntervalMs = Math.max(1_000, refreshIntervalMs);
    }
    public String getBootstrapLocation() { return bootstrapLocation; }
    public void setBootstrapLocation(String bootstrapLocation) {
        this.bootstrapLocation = bootstrapLocation;
    }

    public boolean isAdminApiEnabled() { return adminApiEnabled; }
    public void setAdminApiEnabled(boolean adminApiEnabled) {
        this.adminApiEnabled = adminApiEnabled;
    }

    public boolean isToolSyncEnabled() { return toolSyncEnabled; }
    public void setToolSyncEnabled(boolean toolSyncEnabled) {
        this.toolSyncEnabled = toolSyncEnabled;
    }
    public long getToolSyncIntervalMs() { return toolSyncIntervalMs; }
    public void setToolSyncIntervalMs(long toolSyncIntervalMs) {
        this.toolSyncIntervalMs = Math.max(1_000, toolSyncIntervalMs);
    }
    public long getToolSyncInitialDelayMs() { return toolSyncInitialDelayMs; }
    public void setToolSyncInitialDelayMs(long toolSyncInitialDelayMs) {
        this.toolSyncInitialDelayMs = Math.max(1_000, toolSyncInitialDelayMs);
    }

    public enum RefreshMode {
        POLLING,
        MANUAL
    }
}
