package com.hanwha.ai.document.workflow;

import com.hanwha.ai.document.domain.IndexStatus;
import com.hanwha.ai.document.domain.RagDocument;
import com.hanwha.ai.document.service.RagDocumentRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DocumentIndexWorkflow {
    private static final Logger log = LoggerFactory.getLogger(DocumentIndexWorkflow.class);

    private final List<DocumentIndexTask> tasks;
    private final RagDocumentRepository repository;

    public DocumentIndexWorkflow(List<DocumentIndexTask> tasks, RagDocumentRepository repository) {
        this.tasks = tasks;
        this.repository = repository;
    }

    public void run(RagDocument document) {
        DocumentIndexContext context = new DocumentIndexContext(document);
        try {
            for (DocumentIndexTask task : tasks) {
                if (!task.supports(context)) {
                    continue;
                }
                context.setCurrentTaskName(task.name());
                task.execute(context);
            }
        } catch (Exception exception) {
            String message = rootMessage(exception);
            repository.updateIndexResult(
                    document.getId(),
                    IndexStatus.FAILED.name(),
                    context.storedChunkCount(),
                    message
            );
            log.warn("Document index workflow failed. documentId={} task={} error={}",
                    document.getId(), context.currentTaskName(), message, exception);
        }
    }

    public static String rootMessage(Exception exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            return current.getClass().getSimpleName();
        }
        return message;
    }
}
