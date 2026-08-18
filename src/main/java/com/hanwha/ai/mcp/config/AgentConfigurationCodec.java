package com.hanwha.ai.mcp.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class AgentConfigurationCodec {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String write(AgentConfigurationDocument document) {
        try {
            return objectMapper.writeValueAsString(document);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Agent configuration serialization failed.", exception);
        }
    }

    public AgentConfigurationDocument read(String value) {
        try {
            return objectMapper.readValue(value, AgentConfigurationDocument.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Agent configuration parsing failed.", exception);
        }
    }
}
