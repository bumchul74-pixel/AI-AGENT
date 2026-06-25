package com.hanwha.ai.document.service;

import com.hanwha.ai.document.dto.PythonDocumentIngestResponse;
import com.hanwha.ai.rag.config.RagProperties;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PythonDocumentIngestClient {
    private final RestClient restClient;

    public PythonDocumentIngestClient(RestClient.Builder restClientBuilder, RagProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
    }

    public PythonDocumentIngestResponse ingest(Path filePath, String source, int chunkSize, int overlap) {
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("source", source);
        bodyBuilder.part("chunk_size", chunkSize);
        bodyBuilder.part("overlap", overlap);
        bodyBuilder.part("file", new FileSystemResource(filePath));

        PythonDocumentIngestResponse response = restClient.post()
                .uri("/api/documents/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(bodyBuilder.build())
                .retrieve()
                .body(PythonDocumentIngestResponse.class);

        return response == null ? new PythonDocumentIngestResponse(0) : response;
    }

    public void deleteSource(String source) {
        restClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/documents/source")
                        .queryParam("source", source)
                        .build())
                .retrieve()
                .toBodilessEntity();
    }
}
