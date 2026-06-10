package com.hanwha.ai.llm.domain;

public enum LlmProvider {
    OPENAI;

    public static LlmProvider from(String value) {
        return LlmProvider.valueOf(value.toUpperCase());
    }
}
