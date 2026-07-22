package com.hanwha.ai.generation.domain;

import java.time.LocalDateTime;

public class GenerationHistory {
    private Long id;
    private String targetType;
    private String targetTypesJson;
    private String prompt;
    private String projectKey;
    private String projectStructure;
    private String ragDocumentsJson;
    private String generatedCode;
    private String llmProvider;
    private String llmModel;
    private String neo4jIndexStatus;
    private LocalDateTime neo4jIndexedAt;
    private String neo4jIndexError;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetTypesJson() {
        return targetTypesJson;
    }

    public void setTargetTypesJson(String targetTypesJson) {
        this.targetTypesJson = targetTypesJson;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getProjectStructure() {
        return projectStructure;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public void setProjectStructure(String projectStructure) {
        this.projectStructure = projectStructure;
    }

    public String getRagDocumentsJson() {
        return ragDocumentsJson;
    }

    public void setRagDocumentsJson(String ragDocumentsJson) {
        this.ragDocumentsJson = ragDocumentsJson;
    }

    public String getGeneratedCode() {
        return generatedCode;
    }

    public void setGeneratedCode(String generatedCode) {
        this.generatedCode = generatedCode;
    }

    public String getLlmProvider() {
        return llmProvider;
    }

    public void setLlmProvider(String llmProvider) {
        this.llmProvider = llmProvider;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public String getNeo4jIndexStatus() {
        return neo4jIndexStatus;
    }

    public void setNeo4jIndexStatus(String neo4jIndexStatus) {
        this.neo4jIndexStatus = neo4jIndexStatus;
    }

    public LocalDateTime getNeo4jIndexedAt() {
        return neo4jIndexedAt;
    }

    public void setNeo4jIndexedAt(LocalDateTime neo4jIndexedAt) {
        this.neo4jIndexedAt = neo4jIndexedAt;
    }

    public String getNeo4jIndexError() {
        return neo4jIndexError;
    }

    public void setNeo4jIndexError(String neo4jIndexError) {
        this.neo4jIndexError = neo4jIndexError;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
