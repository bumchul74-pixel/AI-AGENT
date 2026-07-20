package com.hanwha.ai.document.workflow.task;

import com.hanwha.ai.document.config.DocumentProperties;
import com.hanwha.ai.document.domain.DocumentFileSupport;
import com.hanwha.ai.document.domain.IndexStatus;
import com.hanwha.ai.document.dto.PythonDocumentIngestResponse;
import com.hanwha.ai.document.service.PythonDocumentIngestClient;
import com.hanwha.ai.document.service.RagDocumentRepository;
import com.hanwha.ai.document.workflow.DocumentIndexContext;
import com.hanwha.ai.document.workflow.DocumentIndexTask;
import com.hanwha.ai.document.workflow.DocumentIndexWorkflow;
import com.hanwha.ai.global.exception.BusinessException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(40)
public class RagVectorIngestTask implements DocumentIndexTask {
    private final PythonDocumentIngestClient ingestClient;
    private final DocumentProperties properties;
    private final RagDocumentRepository repository;

    public RagVectorIngestTask(
            PythonDocumentIngestClient ingestClient,
            DocumentProperties properties,
            RagDocumentRepository repository
    ) {
        this.ingestClient = ingestClient;
        this.properties = properties;
        this.repository = repository;
    }

    @Override
    public void execute(DocumentIndexContext context) {
        if (!context.isSupportedVectorFile()) {
            String message = "Unsupported document file type for VectorDB indexing. supported="
                    + DocumentFileSupport.supportedExtensionsDescription();
            repository.updateVectorIndexResult(context.document().getId(), IndexStatus.FAILED.name(), 0, message);
            throw new BusinessException(message);
        }

        try {
            PythonDocumentIngestResponse response = ingestClient.ingest(
                    context.filePath(),
                    context.vectorSourceKey(),
                    properties.chunkSize(),
                    properties.overlap()
            );
            context.setStoredChunkCount(response.storedCount());
            repository.updateVectorIndexResult(
                    context.document().getId(),
                    IndexStatus.INDEXED.name(),
                    response.storedCount(),
                    null
            );
            context.document().setVectorIndexStatus(IndexStatus.INDEXED.name());
            context.document().setChunkCount(response.storedCount());
        } catch (Exception exception) {
            String message = DocumentIndexWorkflow.rootMessage(exception);
            repository.updateVectorIndexResult(context.document().getId(), IndexStatus.FAILED.name(), 0, message);
            throw toRuntimeException(exception);
        }
    }

    private RuntimeException toRuntimeException(Exception exception) {
        if (exception instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException(exception);
    }
}
