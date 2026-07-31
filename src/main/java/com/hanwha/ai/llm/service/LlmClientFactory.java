package com.hanwha.ai.llm.service;

import com.hanwha.ai.global.exception.BusinessException;
import com.hanwha.ai.llm.config.LlmProperties;
import com.hanwha.ai.llm.domain.LlmProvider;
import com.hanwha.ai.llm.dto.LlmGenerateRequest;
import com.hanwha.ai.llm.dto.LlmGenerateResponse;
import com.hanwha.ai.llm.exception.LlmRateLimitException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LlmClientFactory {
    private static final Logger log = LoggerFactory.getLogger(LlmClientFactory.class);

    private final LlmProperties properties;
    private final Map<LlmProvider, LlmClient> clients;

    public LlmClientFactory(LlmProperties properties, List<LlmClient> clients) {
        this.properties = properties;
        this.clients = new EnumMap<>(LlmProvider.class);
        clients.forEach(client -> this.clients.put(client.provider(), client));
    }

    public LlmClient current() {
        LlmClient primary = requiredClient(properties.provider());
        return properties.fallbackEnabled() ? fallbackClient(primary) : primary;
    }

    private LlmClient fallbackClient(LlmClient primary) {
        return new LlmClient() {
            private final AtomicReference<LlmProvider> lastProvider =
                    new AtomicReference<>(primary.provider());

            @Override
            public LlmProvider provider() {
                return lastProvider.get();
            }

            @Override
            public LlmGenerateResponse generate(LlmGenerateRequest request) {
                lastProvider.set(primary.provider());
                try {
                    return primary.generate(request);
                } catch (LlmRateLimitException exception) {
                    LlmClient fallback = requiredFallbackClient(primary.provider());
                    log.warn("LLM rate limit reached. Falling back from {} to {}.",
                            primary.provider(), fallback.provider());
                    LlmGenerateResponse response = fallback.generate(request);
                    lastProvider.set(fallback.provider());
                    return response;
                }
            }
        };
    }

    private LlmClient requiredFallbackClient(LlmProvider primaryProvider) {
        if (!StringUtils.hasText(properties.fallbackProvider())) {
            throw new BusinessException("llm.fallback-provider is required when fallback is enabled.");
        }
        LlmClient fallback = requiredClient(properties.fallbackProvider());
        if (fallback.provider() == primaryProvider) {
            throw new BusinessException("LLM fallback provider must differ from the primary provider.");
        }
        return fallback;
    }

    private LlmClient requiredClient(String providerName) {
        LlmProvider provider;
        try {
            provider = LlmProvider.from(providerName);
        } catch (RuntimeException exception) {
            throw new BusinessException("Unsupported LLM provider: " + providerName, exception);
        }
        LlmClient client = clients.get(provider);
        if (client == null) {
            throw new BusinessException("Unsupported LLM provider: " + providerName);
        }
        return client;
    }
}