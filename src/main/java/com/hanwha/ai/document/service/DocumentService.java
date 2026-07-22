package com.hanwha.ai.document.service;

import com.hanwha.ai.document.domain.DocumentType;
import com.hanwha.ai.document.domain.DocumentFileSupport;
import com.hanwha.ai.document.domain.RagDocument;
import com.hanwha.ai.document.dto.DocumentDownload;
import com.hanwha.ai.document.dto.DocumentPageResponse;
import com.hanwha.ai.document.dto.DocumentResponse;
import com.hanwha.ai.document.workflow.DocumentIndexWorkflow;
import com.hanwha.ai.global.exception.BusinessException;
import com.hanwha.ai.sourcegraph.service.SourceGraphService;
import java.util.List;
import java.nio.file.Path;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {
    private final RagDocumentRepository repository;
    private final DocumentStorageService storageService;
    private final PythonDocumentIngestClient ingestClient;
    private final DocumentIndexWorkflow indexWorkflow;
    private final SourceGraphService sourceGraphService;

    public DocumentService(
            RagDocumentRepository repository,
            DocumentStorageService storageService,
            PythonDocumentIngestClient ingestClient,
            DocumentIndexWorkflow indexWorkflow,
            SourceGraphService sourceGraphService
    ) {
        this.repository = repository;
        this.storageService = storageService;
        this.ingestClient = ingestClient;
        this.indexWorkflow = indexWorkflow;
        this.sourceGraphService = sourceGraphService;
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> findAll() {
        return repository.findAll().stream()
                .map(DocumentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> findAll(String projectKey) {
        return repository.findAll(projectKey).stream().map(DocumentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DocumentPageResponse findPage(int page, int size) {
        return findPage(null, page, size);
    }

    @Transactional(readOnly = true)
    public DocumentPageResponse findPage(String projectKey, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        int offset = safePage * safeSize;
        List<DocumentResponse> documents = repository.findPage(projectKey, safeSize, offset).stream()
                .map(DocumentResponse::from)
                .toList();
        long totalCount = repository.countAll(projectKey);
        boolean hasNext = offset + documents.size() < totalCount;
        return new DocumentPageResponse(documents, safePage, safeSize, totalCount, hasNext);
    }

    @Transactional
    public DocumentResponse upload(MultipartFile file, String documentTypeValue) {
        return upload("default", file, documentTypeValue);
    }

    @Transactional
    public DocumentResponse upload(String projectKey, MultipartFile file, String documentTypeValue) {
        requireProject(projectKey);
        StoredDocumentFile storedFile = storageService.store(file);
        return saveAndIndex(projectKey, storedFile, documentTypeValue);
    }

    @Transactional
    public DocumentResponse uploadExtracted(String entryPath, byte[] content, String documentTypeValue) {
        return uploadExtracted("default", entryPath, content, documentTypeValue);
    }

    @Transactional
    public DocumentResponse uploadExtracted(
            String projectKey, String entryPath, byte[] content, String documentTypeValue) {
        requireProject(projectKey);
        StoredDocumentFile storedFile = storageService.store(entryPath, content, "application/octet-stream");
        return saveAndIndex(projectKey, storedFile, documentTypeValue);
    }

    private DocumentResponse saveAndIndex(String projectKey, StoredDocumentFile storedFile, String documentTypeValue) {
        DocumentType documentType = DocumentType.resolve(documentTypeValue, storedFile.originalFileName());
        String fileHash = DocumentFileSupport.sha256(Path.of(storedFile.filePath()));

        // Retries and concurrent directory watchers must be idempotent.
        RagDocument existingDocument = repository.findActiveByProjectAndFileHashAndDocumentType(
                projectKey,
                fileHash,
                documentType.name()
        );
        if (existingDocument != null) {
            storageService.discard(storedFile);
            return DocumentResponse.from(existingDocument);
        }

        RagDocument document = RagDocument.create(
                projectKey,
                storedFile.originalFileName(),
                storedFile.storedFileName(),
                storedFile.filePath(),
                storedFile.fileSize(),
                storedFile.contentType(),
                documentType
        );
        document.setFileHash(fileHash);
        RagDocument savedDocument = repository.save(document);
        if (savedDocument == null) {
            storageService.discard(storedFile);
            throw new BusinessException("Failed to save uploaded document.");
        }
        if (document.getId() == null || !document.getId().equals(savedDocument.getId())) {
            storageService.discard(storedFile);
            return DocumentResponse.from(savedDocument);
        }
        indexWorkflow.run(savedDocument);
        return DocumentResponse.from(requireDocument(savedDocument.getId()));
    }

    @Transactional
    public DocumentResponse reindex(Long id) {
        RagDocument document = requireDocument(id);
        indexWorkflow.run(document);
        return DocumentResponse.from(requireDocument(id));
    }

    @Transactional(readOnly = true)
    public DocumentDownload download(Long id) {
        RagDocument document = requireDocument(id);
        return new DocumentDownload(
                document.getOriginalFileName(),
                document.getContentType(),
                storageService.load(document)
        );
    }

    @Transactional
    public void delete(Long id) {
        RagDocument document = requireDocument(id);
        deleteVectorSources(document);
        sourceGraphService.deleteBySourceKey(document.graphSource());
        storageService.delete(document);
        if (!repository.markDeleted(id)) {
            throw new BusinessException("Document not found.");
        }
    }

    private void deleteVectorSources(RagDocument document) {
        ingestClient.deleteSource(document.ragSource());
        String legacySource = document.legacyRagSource();
        if (!legacySource.equals(document.ragSource())) {
            ingestClient.deleteSource(legacySource);
        }
    }

    private RagDocument requireDocument(Long id) {
        RagDocument document = repository.findById(id);
        if (document == null) {
            throw new BusinessException("Document not found.");
        }
        return document;
    }

    private void requireProject(String projectKey) {
        if (projectKey == null || projectKey.isBlank() || !repository.projectExists(projectKey)) {
            throw new BusinessException("Project not found. Create a Knowledge project before uploading documents.");
        }
    }
}
