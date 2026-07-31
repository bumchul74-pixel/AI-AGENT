package com.hanwha.ai.llm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hanwha.ai.llm.config.LlmProperties;
import com.hanwha.ai.llm.domain.LlmProvider;
import com.hanwha.ai.llm.dto.LlmGenerateRequest;
import com.hanwha.ai.llm.dto.LlmGenerateResponse;
import com.hanwha.ai.llm.exception.LlmRateLimitException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LlmClientFactoryTest {
    @Test
    void fallsBackOnlyWhenPrimaryProviderIsRateLimited() {
        AtomicInteger primaryCalls = new AtomicInteger();
        AtomicInteger fallbackCalls = new AtomicInteger();
        LlmClient primary = client(LlmProvider.GEMINI, request -> {
            primaryCalls.incrementAndGet();
            throw new LlmRateLimitException(LlmProvider.GEMINI, null);
        });
        LlmClient fallback = client(LlmProvider.OPENAI, request -> {
            fallbackCalls.incrementAndGet();
            return new LlmGenerateResponse("fallback response");
        });
        LlmClient routed = new LlmClientFactory(
                new LlmProperties("gemini", true, "openai"),
                List.of(primary, fallback)
        ).current();

        LlmGenerateResponse response = routed.generate(new LlmGenerateRequest("prompt", "context"));

        assertThat(response.content()).isEqualTo("fallback response");
        assertThat(routed.provider()).isEqualTo(LlmProvider.OPENAI);
        assertThat(primaryCalls.get()).isEqualTo(1);
        assertThat(fallbackCalls.get()).isEqualTo(1);
    }

    @Test
    void doesNotFallbackForNonRateLimitFailure() {
        LlmClient primary = client(LlmProvider.GEMINI, request -> {
            throw new IllegalStateException("invalid request");
        });
        LlmClient fallback = client(LlmProvider.OPENAI,
                request -> new LlmGenerateResponse("must not be called"));
        LlmClient routed = new LlmClientFactory(
                new LlmProperties("gemini", true, "openai"),
                List.of(primary, fallback)
        ).current();

        assertThatThrownBy(() -> routed.generate(new LlmGenerateRequest("prompt", "context")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("invalid request");
    }

    private LlmClient client(LlmProvider provider, Generator generator) {
        return new LlmClient() {
            @Override
            public LlmProvider provider() {
                return provider;
            }

            @Override
            public LlmGenerateResponse generate(LlmGenerateRequest request) {
                return generator.generate(request);
            }
        };
    }

    @FunctionalInterface
    private interface Generator {
        LlmGenerateResponse generate(LlmGenerateRequest request);
    }
}