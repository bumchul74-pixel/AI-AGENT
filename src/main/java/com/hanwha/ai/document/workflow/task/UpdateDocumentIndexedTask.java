package com.hanwha.ai.document.workflow.task;

import com.hanwha.ai.document.domain.IndexStatus;
import com.hanwha.ai.document.service.RagDocumentRepository;
import com.hanwha.ai.document.workflow.DocumentIndexContext;
import com.hanwha.ai.document.workflow.DocumentIndexTask;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(60)
public class UpdateDocumentIndexedTask implements DocumentIndexTask {
    private final RagDocumentRepository repository;

    public UpdateDocumentIndexedTask(RagDocumentRepository repository) {
        this.repository = repository;
    }

    @Override
    public void execute(DocumentIndexContext context) {
        repository.updateIndexResult(
                context.document().getId(),
                IndexStatus.INDEXED.name(),
                context.storedChunkCount(),
                null
        );
        context.document().setIndexStatus(IndexStatus.INDEXED.name());
        context.document().setErrorMessage(null);
    }
}
