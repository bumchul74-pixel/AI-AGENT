package com.hanwha.ai.llm.service;

import com.hanwha.ai.global.exception.BusinessException;
import com.hanwha.ai.llm.config.LlmProperties;
import com.hanwha.ai.llm.domain.LlmProvider;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class LlmClientFactory {
    private final LlmProperties properties;
    private final Map<LlmProvider, LlmClient> clients;

    public LlmClientFactory(LlmProperties properties, List<LlmClient> clients) {
        this.properties = properties;
        this.clients = new EnumMap<>(LlmProvider.class);
        clients.forEach(client -> this.clients.put(client.provider(), client));
    }

    public LlmClient current() {
        LlmProvider provider = LlmProvider.from(properties.provider());
        LlmClient client = clients.get(provider);
        if (client == null) {
            throw new BusinessException("Unsupported LLM provider: " + properties.provider());
        }
        return client;
    }
}
