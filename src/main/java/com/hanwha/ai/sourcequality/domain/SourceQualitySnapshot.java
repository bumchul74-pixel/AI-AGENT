package com.hanwha.ai.sourcequality.domain;

import java.time.LocalDateTime;

public class SourceQualitySnapshot {
    private Long id;
    private String projectKey;
    private int totalMethodCount;
    private int duplicateMethodCount;
    private int duplicateGroupCount;
    private double duplicateRatio;
    private int highComplexityCount;
    private int maxCyclomaticComplexity;
    private int maxCognitiveComplexity;
    private String gateStatus;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }
    public int getTotalMethodCount() { return totalMethodCount; }
    public void setTotalMethodCount(int value) { this.totalMethodCount = value; }
    public int getDuplicateMethodCount() { return duplicateMethodCount; }
    public void setDuplicateMethodCount(int value) { this.duplicateMethodCount = value; }
    public int getDuplicateGroupCount() { return duplicateGroupCount; }
    public void setDuplicateGroupCount(int value) { this.duplicateGroupCount = value; }
    public double getDuplicateRatio() { return duplicateRatio; }
    public void setDuplicateRatio(double value) { this.duplicateRatio = value; }
    public int getHighComplexityCount() { return highComplexityCount; }
    public void setHighComplexityCount(int value) { this.highComplexityCount = value; }
    public int getMaxCyclomaticComplexity() { return maxCyclomaticComplexity; }
    public void setMaxCyclomaticComplexity(int value) { this.maxCyclomaticComplexity = value; }
    public int getMaxCognitiveComplexity() { return maxCognitiveComplexity; }
    public void setMaxCognitiveComplexity(int value) { this.maxCognitiveComplexity = value; }
    public String getGateStatus() { return gateStatus; }
    public void setGateStatus(String value) { this.gateStatus = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
