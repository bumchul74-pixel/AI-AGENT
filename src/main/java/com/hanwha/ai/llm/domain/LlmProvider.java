package com.hanwha.ai.llm.domain;

public enum LlmProvider {
    OPENAI,
    GEMINI;

    public static LlmProvider from(String value) {
        return LlmProvider.valueOf(value.toUpperCase());
    }
}
