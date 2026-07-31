package com.hanwha.ai.llm.exception;

import com.hanwha.ai.global.exception.BusinessException;
import com.hanwha.ai.llm.domain.LlmProvider;

public class LlmRateLimitException extends BusinessException {
    private final LlmProvider provider;

    public LlmRateLimitException(LlmProvider provider, Throwable cause) {
        super(provider.name() + " 사용량 한도를 초과했습니다. 잠시 후 다시 시도하거나 LLM 설정을 확인해 주세요.", cause);
        this.provider = provider;
    }

    public LlmProvider getProvider() {
        return provider;
    }
}
