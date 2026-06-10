package com.hanwha.ai.llm.dto;

public record LlmGenerateRequest(
        String prompt,
        String context
) {
}
