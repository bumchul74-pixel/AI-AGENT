package com.hanwha.ai.sourcequality.domain;

public class SourceQualityThreshold {
    public static final int DEFAULT_CYCLOMATIC = 10;
    public static final int DEFAULT_COGNITIVE = 15;
    public static final double DEFAULT_DUPLICATE_RATIO = 10.0;
    public static final int DEFAULT_MINIMUM_DUPLICATE_LINES = 5;

    private String projectKey;
    private int cyclomaticComplexity = DEFAULT_CYCLOMATIC;
    private int cognitiveComplexity = DEFAULT_COGNITIVE;
    private double duplicateRatio = DEFAULT_DUPLICATE_RATIO;
    private int minimumDuplicateLines = DEFAULT_MINIMUM_DUPLICATE_LINES;

    public static SourceQualityThreshold defaults(String projectKey) {
        SourceQualityThreshold threshold = new SourceQualityThreshold();
        threshold.setProjectKey(projectKey);
        return threshold;
    }

    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }
    public int getCyclomaticComplexity() { return cyclomaticComplexity; }
    public void setCyclomaticComplexity(int value) { this.cyclomaticComplexity = value; }
    public int getCognitiveComplexity() { return cognitiveComplexity; }
    public void setCognitiveComplexity(int value) { this.cognitiveComplexity = value; }
    public double getDuplicateRatio() { return duplicateRatio; }
    public void setDuplicateRatio(double duplicateRatio) { this.duplicateRatio = duplicateRatio; }
    public int getMinimumDuplicateLines() { return minimumDuplicateLines; }
    public void setMinimumDuplicateLines(int value) { this.minimumDuplicateLines = value; }
}
