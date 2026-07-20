package com.hanwha.ai.document.workflow.task;

import com.hanwha.ai.document.domain.IndexStatus;
import com.hanwha.ai.document.service.RagDocumentRepository;
import com.hanwha.ai.document.workflow.DocumentIndexContext;
import com.hanwha.ai.document.workflow.DocumentIndexTask;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class MarkDocumentIndexingTask implements DocumentIndexTask {
    private final RagDocumentRepository repository;

    public MarkDocumentIndexingTask(RagDocumentRepository repository) {
        this.repository = repository;
    }

    @Override
    public void execute(DocumentIndexContext context) {
        repository.updateIndexStatus(context.document().getId(), IndexStatus.INDEXING.name(), null);
        repository.updateVectorIndexStatus(context.document().getId(), IndexStatus.INDEXING.name(), null);
        context.document().setIndexStatus(IndexStatus.INDEXING.name());
        context.document().setVectorIndexStatus(IndexStatus.INDEXING.name());

        if (context.isJavaSourceFile()) {
            repository.updateGraphIndexStatus(context.document().getId(), IndexStatus.INDEXING.name(), null);
            context.document().setGraphIndexStatus(IndexStatus.INDEXING.name());
        }
    }
}
