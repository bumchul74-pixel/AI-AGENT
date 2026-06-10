package com.hanwha.ai.llm.service;

import com.hanwha.ai.global.exception.BusinessException;
import com.hanwha.ai.llm.config.OpenAiProperties;
import com.hanwha.ai.llm.domain.LlmProvider;
import com.hanwha.ai.llm.dto.LlmGenerateRequest;
import com.hanwha.ai.llm.dto.LlmGenerateResponse;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

@Component
public class OpenAiLlmClient implements LlmClient {
    private final OpenAiProperties properties;
    private final RestClient restClient;

    public OpenAiLlmClient(OpenAiProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
    }

    @Override
    public LlmProvider provider() {
        return LlmProvider.OPENAI;
    }

    @Override
    public LlmGenerateResponse generate(LlmGenerateRequest request) {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new BusinessException("OPENAI_API_KEY is required.");
        }

        try {
            JsonNode response = restClient.post()
                    .uri("/responses")
                    .headers(headers -> headers.setBearerAuth(properties.apiKey()))
                    .body(Map.of(
                            "model", properties.model(),
                            "input", buildInput(request)
                    ))
                    .retrieve()
                    .body(JsonNode.class);

            return new LlmGenerateResponse(extractText(response));
        } catch (RestClientException exception) {
            throw new BusinessException("OpenAI API request failed.");
        }
    }

    private String buildInput(LlmGenerateRequest request) {
        return """
                Prompt:
                %s

                RAG Context:
                %s
                """.formatted(request.prompt(), request.context());
    }

    private String extractText(JsonNode response) {
        if (response == null) {
            return "";
        }

        JsonNode outputText = response.get("output_text");
        if (outputText != null && outputText.isTextual()) {
            return outputText.asText();
        }

        StringBuilder builder = new StringBuilder();
        JsonNode output = response.get("output");
        if (output != null && output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.get("content");
                if (content == null || !content.isArray()) {
                    continue;
                }
                for (JsonNode contentItem : content) {
                    JsonNode text = contentItem.get("text");
                    if (text != null && text.isTextual()) {
                        builder.append(text.asText()).append(System.lineSeparator());
                    }
                }
            }
        }

        return builder.toString().trim();
    }
}
