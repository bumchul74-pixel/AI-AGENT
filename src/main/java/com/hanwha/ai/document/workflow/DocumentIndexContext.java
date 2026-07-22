package com.hanwha.ai.document.workflow;

import com.hanwha.ai.document.domain.DocumentFileSupport;
import com.hanwha.ai.document.domain.RagDocument;
import com.hanwha.ai.sourcegraph.domain.SourceGraphIdentity;
import java.nio.file.Path;

public class DocumentIndexContext {
    private final RagDocument document;
    private int storedChunkCount;
    private String currentTaskName;

    public DocumentIndexContext(RagDocument document) {
        this.document = document;
    }

    public RagDocument document() {
        return document;
    }

    public Path filePath() {
        return Path.of(document.getFilePath());
    }

    public boolean isSupportedVectorFile() {
        return DocumentFileSupport.isSupportedVectorFile(document.getOriginalFileName());
    }

    public boolean isJavaSourceFile() {
        return DocumentFileSupport.isJavaSourceFile(document.getOriginalFileName());
    }

    public boolean isGraphSourceFile() {
        return DocumentFileSupport.isGraphSourceFile(document.getOriginalFileName());
    }

    public String vectorSourceKey() {
        return document.ragSource();
    }

    public String graphSourceKey() {
        return document.graphSource();
    }

    public String logicalFilePath() {
        return SourceGraphIdentity.normalizeFilePath(
                document.getOriginalFileName(),
                document.getOriginalFileName()
        );
    }

    public int storedChunkCount() {
        return storedChunkCount;
    }

    public void setStoredChunkCount(int storedChunkCount) {
        this.storedChunkCount = storedChunkCount;
    }

    public String currentTaskName() {
        return currentTaskName;
    }

    public void setCurrentTaskName(String currentTaskName) {
        this.currentTaskName = currentTaskName;
    }
}
