package com.hanwha.ai.generation.domain;

import java.time.LocalDateTime;

public class GenerationHistory {
    private Long id;
    private String targetType;
    private String targetTypesJson;
    private String prompt;
    private String projectStructure;
    private String ragDocumentsJson;
    private String generatedCode;
    private String llmProvider;
    private String llmModel;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
