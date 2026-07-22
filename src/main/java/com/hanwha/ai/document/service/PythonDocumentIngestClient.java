package com.hanwha.ai.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanwha.ai.document.dto.PythonDocumentIngestResponse;
import com.hanwha.ai.rag.config.RagProperties;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PythonDocumentIngestClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PythonDocumentIngestClient(
            RestClient.Builder restClientBuilder,
            RagProperties properties
    ) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
    }

    public PythonDocumentIngestResponse ingest(
            Path filePath,
            String source,
            int chunkSize,
            int overlap,
            String projectId,
            String logicalFilePath,
            String fileHash,
            List<String> entityIds,
            Long documentId,
            String symbol,
            Map<String, Object> metadata
    ) {
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("source", source);
        bodyBuilder.part("chunk_size", chunkSize);
        bodyBuilder.part("overlap", overlap);
        bodyBuilder.part("project_id", projectId);
        bodyBuilder.part("file_path", logicalFilePath);
        bodyBuilder.part("file_hash", fileHash == null ? "" : fileHash);
        bodyBuilder.part("entity_ids", objectMapper.valueToTree(entityIds).toString());
        if (documentId != null) {
            bodyBuilder.part("document_id", documentId);
        }
        bodyBuilder.part("symbol", symbol == null ? "" : symbol);
        bodyBuilder.part("metadata", objectMapper.valueToTree(metadata).toString());
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
