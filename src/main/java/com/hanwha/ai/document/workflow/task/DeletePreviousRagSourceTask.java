package com.hanwha.ai.document.workflow.task;

import com.hanwha.ai.document.service.PythonDocumentIngestClient;
import com.hanwha.ai.document.workflow.DocumentIndexContext;
import com.hanwha.ai.document.workflow.DocumentIndexTask;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(30)
public class DeletePreviousRagSourceTask implements DocumentIndexTask {
    private final PythonDocumentIngestClient ingestClient;

    public DeletePreviousRagSourceTask(PythonDocumentIngestClient ingestClient) {
        this.ingestClient = ingestClient;
    }

    @Override
    public void execute(DocumentIndexContext context) {
        ingestClient.deleteSource(context.vectorSourceKey());

        String legacySource = context.document().legacyRagSource();
        if (!legacySource.equals(context.vectorSourceKey())) {
            ingestClient.deleteSource(legacySource);
        }
    }
}
