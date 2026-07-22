package com.hanwha.ai.rag.service;

import com.hanwha.ai.rag.config.RagProperties;
import com.hanwha.ai.rag.dto.RagSearchRequest;
import com.hanwha.ai.rag.dto.RagSearchResponse;
import com.hanwha.ai.rag.dto.RagStatsResponse;
import com.hanwha.ai.rag.dto.ChunkBatchRequest;
import com.hanwha.ai.rag.dto.ChunkBatchResponse;
import com.hanwha.ai.rag.dto.RagChunkResult;
import com.hanwha.ai.rag.dto.RagSourceResponse;
import java.util.Arrays;
import java.util.List;
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

    @Override
    public RagStatsResponse stats() {
        try {
            RagStatsResponse response = restClient.get()
                    .uri("/api/stats")
                    .retrieve()
                    .body(RagStatsResponse.class);
            return response == null ? RagStatsResponse.empty() : response;
        } catch (RestClientException exception) {
            return RagStatsResponse.empty();
        }
    }

    @Override
    public List<RagChunkResult> findChunks(List<String> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return List.of();
        }
        try {
            ChunkBatchResponse response = restClient.post()
                    .uri("/api/chunks/by-ids")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ChunkBatchRequest(chunkIds))
                    .retrieve()
                    .body(ChunkBatchResponse.class);
            return response == null ? List.of() : response.chunks();
        } catch (RestClientException exception) {
            return List.of();
        }
    }

    @Override
    public List<RagSourceResponse> findSources() {
        try {
            RagSourceResponse[] response = restClient.get()
                    .uri("/api/sources")
                    .retrieve()
                    .body(RagSourceResponse[].class);
            return response == null ? List.of() : Arrays.asList(response);
        } catch (RestClientException exception) {
            return List.of();
        }
    }

    @Override
    public int deleteSource(String sourceKey) {
        try {
            DeleteSourceResponse response = restClient.delete()
                    .uri(uriBuilder -> uriBuilder.path("/api/documents/source")
                            .queryParam("source", sourceKey)
                            .build())
                    .retrieve()
                    .body(DeleteSourceResponse.class);
            return response == null ? 0 : response.deletedCount();
        } catch (RestClientException exception) {
            throw new IllegalStateException("Failed to delete VectorDB source: " + sourceKey, exception);
        }
    }

    private record DeleteSourceResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("deleted_count") int deletedCount
    ) {
    }
}
