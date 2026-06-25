package com.hanwha.ai.document.service;

import com.hanwha.ai.document.config.DocumentProperties;
import com.hanwha.ai.document.domain.DocumentType;
import com.hanwha.ai.document.domain.IndexStatus;
import com.hanwha.ai.document.domain.RagDocument;
import com.hanwha.ai.document.dto.DocumentDownload;
import com.hanwha.ai.document.dto.DocumentResponse;
import com.hanwha.ai.document.dto.PythonDocumentIngestResponse;
import com.hanwha.ai.global.exception.BusinessException;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {
    private final RagDocumentRepository repository;
    private final DocumentStorageService storageService;
    private final PythonDocumentIngestClient ingestClient;
    private final DocumentProperties properties;

    public DocumentService(
            RagDocumentRepository repository,
            DocumentStorageService storageService,
            PythonDocumentIngestClient ingestClient,
            DocumentProperties properties
    ) {
        this.repository = repository;
        this.storageService = storageService;
        this.ingestClient = ingestClient;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> findAll() {
        return repository.findAll().stream()
                .map(DocumentResponse::from)
                .toList();
    }

    @Transactional
    public DocumentResponse upload(MultipartFile file, String documentTypeValue) {
        StoredDocumentFile storedFile = storageService.store(file);
        DocumentType documentType = DocumentType.resolve(documentTypeValue, storedFile.originalFileName());
        RagDocument document = RagDocument.create(
                storedFile.originalFileName(),
                storedFile.storedFileName(),
                storedFile.filePath(),
                storedFile.fileSize(),
                storedFile.contentType(),
                documentType
        );
        RagDocument savedDocument = repository.save(document);
        return index(savedDocument);
    }

    @Transactional
    public DocumentResponse reindex(Long id) {
        return index(requireDocument(id));
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
        ingestClient.deleteSource(document.ragSource());
        storageService.delete(document);
        if (!repository.markDeleted(id)) {
            throw new BusinessException("Document not found.");
        }
    }

    private DocumentResponse index(RagDocument document) {
        repository.updateIndexStatus(document.getId(), IndexStatus.INDEXING.name(), null);
        try {
            ingestClient.deleteSource(document.ragSource());
            PythonDocumentIngestResponse response = ingestClient.ingest(
                    Path.of(document.getFilePath()),
                    document.ragSource(),
                    properties.chunkSize(),
                    properties.overlap()
            );
            repository.updateIndexResult(
                    document.getId(),
                    IndexStatus.INDEXED.name(),
                    response.storedCount(),
                    null
            );
        } catch (Exception exception) {
            repository.updateIndexResult(
                    document.getId(),
                    IndexStatus.FAILED.name(),
                    0,
                    rootMessage(exception)
            );
        }
        return DocumentResponse.from(requireDocument(document.getId()));
    }

    private RagDocument requireDocument(Long id) {
        RagDocument document = repository.findById(id);
        if (document == null) {
            throw new BusinessException("Document not found.");
        }
        return document;
    }

    private String rootMessage(Exception exception) {
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
