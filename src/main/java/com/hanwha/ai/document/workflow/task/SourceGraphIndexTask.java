package com.hanwha.ai.document.workflow.task;

import com.hanwha.ai.document.domain.IndexStatus;
import com.hanwha.ai.document.config.DocumentProperties;
import com.hanwha.ai.document.service.RagDocumentRepository;
import com.hanwha.ai.document.workflow.DocumentIndexContext;
import com.hanwha.ai.document.workflow.DocumentIndexTask;
import com.hanwha.ai.document.workflow.DocumentIndexWorkflow;
import com.hanwha.ai.global.exception.BusinessException;
import com.hanwha.ai.sourcegraph.domain.SourceGraphIndexStatus;
import com.hanwha.ai.sourcegraph.dto.JavaSourceGraphIngestRequest;
import com.hanwha.ai.sourcegraph.dto.SourceGraphIndexResult;
import com.hanwha.ai.sourcegraph.service.SourceGraphService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.stream.IntStream;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(50)
public class SourceGraphIndexTask implements DocumentIndexTask {
    private final SourceGraphService sourceGraphService;
    private final RagDocumentRepository repository;
    private final DocumentProperties properties;

    public SourceGraphIndexTask(
            SourceGraphService sourceGraphService,
            RagDocumentRepository repository,
            DocumentProperties properties
    ) {
        this.sourceGraphService = sourceGraphService;
        this.repository = repository;
        this.properties = properties;
    }

    @Override
    public boolean supports(DocumentIndexContext context) {
        return context.isGraphSourceFile();
    }

    @Override
    public void execute(DocumentIndexContext context) {
        try {
            String content = Files.readString(context.filePath(), StandardCharsets.UTF_8);
            SourceGraphIndexResult result = sourceGraphService.indexJavaSource(new JavaSourceGraphIngestRequest(
                    context.graphSourceKey(),
                    context.document().getOriginalFileName(),
                    content,
                    properties.projectId(),
                    properties.moduleName(),
                    context.logicalFilePath(),
                    context.document().getFileHash(),
                    IntStream.range(0, context.storedChunkCount())
                            .mapToObj(index -> context.vectorSourceKey() + ":chunk:" + index)
                            .toList()
            ));
            SourceGraphIndexStatus status = result == null ? SourceGraphIndexStatus.SKIPPED : result.status();
            if (SourceGraphIndexStatus.FAILED.equals(status)) {
                String message = result.errorMessage();
                repository.updateGraphIndexStatus(context.document().getId(), IndexStatus.FAILED.name(), message);
                throw new BusinessException(message == null ? "SourceGraph indexing failed." : message);
            }

            String mappedStatus = SourceGraphIndexStatus.SUCCESS.equals(status)
                    ? IndexStatus.INDEXED.name()
                    : IndexStatus.SKIPPED.name();
            String message = result == null ? "SourceGraph index result was empty." : result.errorMessage();
            repository.updateGraphIndexStatus(context.document().getId(), mappedStatus, message);
            context.document().setGraphIndexStatus(mappedStatus);
        } catch (Exception exception) {
            String message = DocumentIndexWorkflow.rootMessage(exception);
            repository.updateGraphIndexStatus(context.document().getId(), IndexStatus.FAILED.name(), message);
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
