package com.hanwha.ai.rag.service;

import com.hanwha.ai.rag.config.RagProperties;
import com.hanwha.ai.rag.dto.RagSearchRequest;
import com.hanwha.ai.rag.dto.RagSearchResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PythonRagClient implements RagClient {
    private final RestClient restClient;
    private final RagProperties properties;

    public PythonRagClient(RestClient.Builder restClientBuilder, RagProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
    }

    @Override
    public RagSearchResponse search(RagSearchRequest request) {
        try {
            RagSearchResponse response = restClient.post()
                    .uri(properties.searchPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(RagSearchResponse.class);
            return response == null ? RagSearchResponse.empty() : response;
        } catch (RestClientException exception) {
            return RagSearchResponse.empty();
        }
    }
}
