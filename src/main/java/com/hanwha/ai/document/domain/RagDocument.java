package com.hanwha.ai.document.domain;

import java.time.LocalDateTime;

public class RagDocument {
    private Long id;
    private String projectKey;
    private String originalFileName;
    private String storedFileName;
    private String filePath;
    private Long fileSize;
    private String contentType;
    private String documentType;
    private String fileHash;
    private String vectorSourceKey;
    private String graphSourceKey;
    private String indexStatus;
    private String vectorIndexStatus;
    private String graphIndexStatus;
    private Integer chunkCount;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    public static RagDocument create(
            String originalFileName,
            String storedFileName,
            String filePath,
            Long fileSize,
            String contentType,
            DocumentType documentType
    ) {
        return create("default", originalFileName, storedFileName, filePath, fileSize, contentType, documentType);
    }

    public static RagDocument create(
            String projectKey,
            String originalFileName,
            String storedFileName,
            String filePath,
            Long fileSize,
            String contentType,
            DocumentType documentType
    ) {
        RagDocument document = new RagDocument();
        document.setProjectKey(projectKey);
        document.setOriginalFileName(originalFileName);
        document.setStoredFileName(storedFileName);
        document.setFilePath(filePath);
        document.setFileSize(fileSize);
        document.setContentType(contentType);
        document.setDocumentType(documentType.name());
        document.setIndexStatus(IndexStatus.PENDING.name());
        document.setVectorIndexStatus(IndexStatus.PENDING.name());
        if (DocumentFileSupport.isGraphSourceFile(originalFileName)) {
            document.setGraphIndexStatus(IndexStatus.PENDING.name());
        }
        document.setChunkCount(0);
        return document;
    }

    public static String sourceKey(Long id) {
        return "document:" + id;
    }

    public String ragSource() {
        if (hasText(vectorSourceKey)) {
            return vectorSourceKey;
        }
        if (id != null) {
            return sourceKey(id);
        }
        return legacyRagSource();
    }

    public String graphSource() {
        if (hasText(graphSourceKey)) {
            return graphSourceKey;
        }
        return ragSource();
    }

    public String legacyRagSource() {
        return "document:" + id + ":" + originalFileName;
    }

    public boolean isJavaSourceFile() {
        return DocumentFileSupport.isJavaSourceFile(originalFileName);
    }

    public boolean isGraphSourceFile() {
        return DocumentFileSupport.isGraphSourceFile(originalFileName);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public Long getId() {
        return id;
    }

    public String getProjectKey() { return projectKey; }

    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getStoredFileName() {
        return storedFileName;
    }

    public void setStoredFileName(String storedFileName) {
        this.storedFileName = storedFileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public String getVectorSourceKey() {
        return vectorSourceKey;
    }

    public void setVectorSourceKey(String vectorSourceKey) {
        this.vectorSourceKey = vectorSourceKey;
    }

    public String getGraphSourceKey() {
        return graphSourceKey;
    }

    public void setGraphSourceKey(String graphSourceKey) {
        this.graphSourceKey = graphSourceKey;
    }

    public String getIndexStatus() {
        return indexStatus;
    }

    public void setIndexStatus(String indexStatus) {
        this.indexStatus = indexStatus;
    }

    public String getVectorIndexStatus() {
        return vectorIndexStatus;
    }

    public void setVectorIndexStatus(String vectorIndexStatus) {
        this.vectorIndexStatus = vectorIndexStatus;
    }

    public String getGraphIndexStatus() {
        return graphIndexStatus;
    }

    public void setGraphIndexStatus(String graphIndexStatus) {
        this.graphIndexStatus = graphIndexStatus;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(Integer chunkCount) {
        this.chunkCount = chunkCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
