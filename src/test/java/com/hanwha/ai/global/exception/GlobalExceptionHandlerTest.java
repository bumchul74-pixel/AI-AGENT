package com.hanwha.ai.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanwha.ai.llm.domain.LlmProvider;
import com.hanwha.ai.llm.exception.LlmRateLimitException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GlobalExceptionHandlerTest {
    @Test
    void returnsTooManyRequestsForLlmRateLimit() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleLlmRateLimitException(
                new LlmRateLimitException(LlmProvider.GEMINI, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("GEMINI 사용량 한도를 초과했습니다");
    }
}