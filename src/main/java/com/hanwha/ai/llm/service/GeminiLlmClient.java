package com.hanwha.ai.llm.service;

import com.hanwha.ai.global.exception.BusinessException;
import com.hanwha.ai.llm.config.GeminiProperties;
import com.hanwha.ai.llm.domain.LlmProvider;
import com.hanwha.ai.llm.dto.LlmGenerateRequest;
import com.hanwha.ai.llm.dto.LlmGenerateResponse;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

@Component
public class GeminiLlmClient implements LlmClient {
    private static final Logger log = LoggerFactory.getLogger(GeminiLlmClient.class);

    private final GeminiProperties properties;
    private final RestClient restClient;

    public GeminiLlmClient(GeminiProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .build();
    }

    @Override
    public LlmProvider provider() {
        return LlmProvider.GEMINI;
    }

    @Override
    public LlmGenerateResponse generate(LlmGenerateRequest request) {
        validateProperties();

        try {
            log.debug("Requesting Gemini GenerateContent API. model={} baseUrl={} promptLength={} contextLength={}",
                    properties.model(),
                    properties.baseUrl(),
                    request.prompt().length(),
                    request.context().length());

            JsonNode response = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", properties.model())
                    .headers(headers -> headers.set("x-goog-api-key", properties.apiKey()))
                    .body(Map.of(
                            "contents", List.of(Map.of(
                                    "parts", List.of(Map.of("text", buildPrompt(request)))
                            ))
                    ))
                    .retrieve()
                    .body(JsonNode.class);

            return new LlmGenerateResponse(extractText(response));
        } catch (RestClientResponseException exception) {
            log.error(
                    "Gemini API request failed. status={} responseBody={}",
                    exception.getStatusCode(),
                    exception.getResponseBodyAsString(),
                    exception
            );
            throw new BusinessException(buildLlmResponseErrorMessage(exception), exception);
        } catch (RestClientException exception) {
            log.error(
                    "Gemini API request failed before receiving a response. model={} baseUrl={} message={}",
                    properties.model(),
                    properties.baseUrl(),
                    exception.getMessage(),
                    exception
            );
            throw new BusinessException("Gemini API request failed.", exception);
        }
    }

    private String buildLlmResponseErrorMessage(RestClientResponseException exception) {
        String responseBody = exception.getResponseBodyAsString();
        String code = firstJsonNumber(responseBody, "code");
        if (!StringUtils.hasText(code)) {
            code = String.valueOf(exception.getStatusCode().value());
        }

        String message = firstJsonString(responseBody, "message");
        if (!StringUtils.hasText(message)) {
            message = StringUtils.hasText(exception.getStatusText())
                    ? exception.getStatusText()
                    : responseBody;
        }

        return "LLM response code=" + code + ", message=" + message;
    }

    private String firstJsonNumber(String json, String fieldName) {
        if (!StringUtils.hasText(json)) {
            return "";
        }

        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(fieldName) + "\\\"\\s*:\\s*(\\d+)").matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String firstJsonString(String json, String fieldName) {
        if (!StringUtils.hasText(json)) {
            return "";
        }

        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(fieldName) + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\\\\\"])*)\\\"").matcher(json);
        return matcher.find() ? unescapeJsonString(matcher.group(1)) : "";
    }

    private String unescapeJsonString(String value) {
        return value
                .replace("\\\"", "\"")
                .replace("\\/", "/")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }

    private void validateProperties() {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new BusinessException("GEMINI_API_KEY is required.");
        }
        if (!StringUtils.hasText(properties.baseUrl())) {
            throw new BusinessException("gemini.base-url is required.");
        }
        if (!StringUtils.hasText(properties.model())) {
            throw new BusinessException("gemini.model is required.");
        }
    }

    private String buildPrompt(LlmGenerateRequest request) {
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

        StringBuilder builder = new StringBuilder();
        JsonNode candidates = response.get("candidates");
        if (candidates != null && candidates.isArray()) {
            for (JsonNode candidate : candidates) {
                appendPartsText(builder, candidate.get("content"));
            }
        }

        return builder.toString().trim();
    }

    private void appendPartsText(StringBuilder builder, JsonNode content) {
        if (content == null) {
            return;
        }

        JsonNode parts = content.get("parts");
        if (parts == null || !parts.isArray()) {
            return;
        }

        for (JsonNode part : parts) {
            JsonNode text = part.get("text");
            if (text != null && text.isTextual()) {
                builder.append(text.asText()).append(System.lineSeparator());
            }
        }
    }
}