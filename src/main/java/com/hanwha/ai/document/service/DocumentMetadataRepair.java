package com.hanwha.ai.document.service;

import com.hanwha.ai.document.domain.DocumentFileSupport;
import com.hanwha.ai.document.domain.RagDocument;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Repairs metadata written by older upload/index workflow versions. */
@Component
public class DocumentMetadataRepair {
    private static final Logger log = LoggerFactory.getLogger(DocumentMetadataRepair.class);

    private final RagDocumentRepository repository;

    public DocumentMetadataRepair(RagDocumentRepository repository) {
        this.repository = repository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void repair() {
        for (RagDocument document : repository.findAll()) {
            if (document.getId() == null || document.getFilePath() == null) {
                continue;
            }

            Path filePath = Path.of(document.getFilePath());
            if (!Files.isRegularFile(filePath)) {
                log.warn("Skipping document metadata repair because the file is missing. documentId={} path={}",
                        document.getId(), filePath);
                continue;
            }

            try {
                String fileHash = DocumentFileSupport.sha256(filePath);
                RagDocument duplicate = repository.findActiveByProjectAndFileHashAndDocumentType(
                        document.getProjectKey(),
                        fileHash,
                        document.getDocumentType()
                );
                if (duplicate != null && !document.getId().equals(duplicate.getId())) {
                    repository.markDeleted(document.getId());
                    log.warn("Marked duplicate document as deleted during metadata repair. documentId={} duplicateId={}",
                            document.getId(), duplicate.getId());
                    continue;
                }

                String vectorSourceKey = RagDocument.sourceKey(document.getId());
                String graphSourceKey = document.isGraphSourceFile() ? vectorSourceKey : null;
                if (!fileHash.equals(document.getFileHash())
                        || !vectorSourceKey.equals(document.getVectorSourceKey())
                        || !equalsNullable(graphSourceKey, document.getGraphSourceKey())) {
                    repository.updateIndexMetadata(
                            document.getId(),
                            fileHash,
                            vectorSourceKey,
                            graphSourceKey
                    );
                }
            } catch (RuntimeException exception) {
                log.warn("Failed to repair document metadata. documentId={} path={}",
                        document.getId(), filePath, exception);
            }
        }
    }

    private boolean equalsNullable(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
