package com.hanwha.ai.generation.dto;

import java.util.List;

public record GenerationRequest(
        List<String> targetTypes,
        String prompt,
        String projectStructure,
        String projectKey
) {
    public GenerationRequest(List<String> targetTypes, String prompt, String projectStructure) {
        this(targetTypes, prompt, projectStructure, null);
    }
}
