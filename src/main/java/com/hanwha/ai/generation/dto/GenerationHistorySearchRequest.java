package com.hanwha.ai.generation.dto;

import java.time.LocalDateTime;

public class GenerationHistorySearchRequest {
    private final Long id;
    private final String targetType;
    private final String targetTypes;
    private final String prompt;
    private final String projectStructure;
    private final String ragDocuments;
    private final String generatedCode;
    private final String llmProvider;
    private final String llmModel;
    private final LocalDateTime createdFrom;
    private final LocalDateTime createdTo;

    public GenerationHistorySearchRequest(
            Long id,
            String targetType,
            String targetTypes,
            String prompt,
            String projectStructure,
            String ragDocuments,
            String generatedCode,
            String llmProvider,
            String llmModel,
            LocalDateTime createdFrom,
            LocalDateTime createdTo
    ) {
        this.id = id;
        this.targetType = targetType;
        this.targetTypes = targetTypes;
        this.prompt = prompt;
        this.projectStructure = projectStructure;
        this.ragDocuments = ragDocuments;
        this.generatedCode = generatedCode;
        this.llmProvider = llmProvider;
        this.llmModel = llmModel;
        this.createdFrom = createdFrom;
        this.createdTo = createdTo;
    }

    public static GenerationHistorySearchRequest empty() {
        return new GenerationHistorySearchRequest(null, null, null, null, null, null, null, null, null, null, null);
    }

    public Long getId() {
        return id;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetTypes() {
        return targetTypes;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getProjectStructure() {
        return projectStructure;
    }

    public String getRagDocuments() {
        return ragDocuments;
    }

    public String getGeneratedCode() {
        return generatedCode;
    }

    public String getLlmProvider() {
        return llmProvider;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public LocalDateTime getCreatedFrom() {
        return createdFrom;
    }

    public LocalDateTime getCreatedTo() {
        return createdTo;
    }
}