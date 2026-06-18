package com.hanwha.ai.llm.service;

import com.hanwha.ai.global.exception.BusinessException;
import com.hanwha.ai.llm.config.OpenAiProperties;
import com.hanwha.ai.llm.domain.LlmProvider;
import com.hanwha.ai.llm.dto.LlmGenerateRequest;
import com.hanwha.ai.llm.dto.LlmGenerateResponse;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

@Component
public class OpenAiLlmClient implements LlmClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmClient.class);

    private final OpenAiProperties properties;
    private final RestClient restClient;

    public OpenAiLlmClient(OpenAiProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = buildRestClient(restClientBuilder);
    }

    @Override
    public LlmProvider provider() {
        return LlmProvider.OPENAI;
    }

    private RestClient buildRestClient(RestClient.Builder restClientBuilder) {
        RestClient.Builder builder = restClientBuilder.baseUrl(properties.baseUrl());

        if (properties.sslTrustAll()) {
            log.warn("OpenAI SSL certificate validation is disabled. Use this only for local development or trusted internal networks.");
            builder.requestFactory(new JdkClientHttpRequestFactory(buildTrustAllHttpClient()));
        }

        return builder.build();
    }

    private HttpClient buildTrustAllHttpClient() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustAllManager()}, new SecureRandom());

            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .build();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to initialize trust-all SSL context.", exception);
        }
    }

    private X509TrustManager trustAllManager() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }

    @Override
    public LlmGenerateResponse generate(LlmGenerateRequest request) {
        if (!StringUtils.hasText(properties.apiKey())) {
            log.warn("OpenAI API key is missing. model={} baseUrl={}", properties.model(), properties.baseUrl());
            throw new BusinessException("OPENAI_API_KEY is required.");
        }

        try {
            log.debug("Requesting OpenAI Responses API. model={} baseUrl={} promptLength={} contextLength={}",
                    properties.model(),
                    properties.baseUrl(),
                    request.prompt().length(),
                    request.context().length());

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
        } catch (RestClientResponseException exception) {
            log.error(
                    "OpenAI API request failed. status={} responseBody={}",
                    exception.getStatusCode(),
                    exception.getResponseBodyAsString(),
                    exception
            );
            throw new BusinessException("OpenAI API request failed.", exception);
        } catch (RestClientException exception) {
            log.error(
                    "OpenAI API request failed before receiving a response. model={} baseUrl={} message={}",
                    properties.model(),
                    properties.baseUrl(),
                    exception.getMessage(),
                    exception
            );
            throw new BusinessException("OpenAI API request failed.", exception);
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
