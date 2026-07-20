package com.hanwha.ai.document.workflow.task;

import com.hanwha.ai.document.domain.RagDocument;
import com.hanwha.ai.document.domain.DocumentFileSupport;
import com.hanwha.ai.document.service.RagDocumentRepository;
import com.hanwha.ai.document.workflow.DocumentIndexContext;
import com.hanwha.ai.document.workflow.DocumentIndexTask;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class PrepareDocumentIndexMetadataTask implements DocumentIndexTask {
    private final RagDocumentRepository repository;

    public PrepareDocumentIndexMetadataTask(RagDocumentRepository repository) {
        this.repository = repository;
    }

    @Override
    public void execute(DocumentIndexContext context) {
        RagDocument document = context.document();
        String sourceKey = RagDocument.sourceKey(document.getId());
        String graphSourceKey = context.isJavaSourceFile() ? sourceKey : null;
        String fileHash = document.getFileHash();
        if (fileHash == null || fileHash.isBlank()) {
            fileHash = DocumentFileSupport.sha256(context.filePath());
        }

        document.setFileHash(fileHash);
        document.setVectorSourceKey(sourceKey);
        document.setGraphSourceKey(graphSourceKey);
        repository.updateIndexMetadata(document.getId(), fileHash, sourceKey, graphSourceKey);
    }

}
