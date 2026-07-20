package com.hanwha.ai.document.service;

import com.hanwha.ai.document.domain.RagDocument;
import com.hanwha.ai.document.mapper.RagDocumentMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class RagDocumentRepository {
    private final RagDocumentMapper mapper;

    public RagDocumentRepository(RagDocumentMapper mapper) {
        this.mapper = mapper;
    }

    public RagDocument save(RagDocument document) {
        mapper.insert(document);
        // ON CONFLICT DO NOTHING leaves the generated id empty. Resolve that
        // case to the already active document on upload retries.
        if (document.getId() == null
                && document.getFileHash() != null
                && document.getDocumentType() != null) {
            return findActiveByFileHashAndDocumentType(document.getFileHash(), document.getDocumentType());
        }
        return findById(document.getId());
    }

    public List<RagDocument> findAll() {
        return mapper.findAll();
    }

    public List<RagDocument> findPage(int limit, int offset) {
        return mapper.findPage(limit, offset);
    }

    public long countAll() {
        return mapper.countAll();
    }

    public RagDocument findById(Long id) {
        return mapper.findById(id);
    }
    public RagDocument findActiveByFileHashAndDocumentType(String fileHash, String documentType) {
        return mapper.findActiveByFileHashAndDocumentType(fileHash, documentType);
    }

    public RagDocument findByGraphSourceKey(String graphSourceKey) {
        return mapper.findByGraphSourceKey(graphSourceKey);
    }

    public void updateIndexMetadata(Long id, String fileHash, String vectorSourceKey, String graphSourceKey) {
        mapper.updateIndexMetadata(id, fileHash, vectorSourceKey, graphSourceKey);
    }

    public void updateIndexStatus(Long id, String indexStatus, String errorMessage) {
        mapper.updateIndexStatus(id, indexStatus, errorMessage);
    }

    public void updateVectorIndexStatus(Long id, String vectorIndexStatus, String errorMessage) {
        mapper.updateVectorIndexStatus(id, vectorIndexStatus, errorMessage);
    }

    public void updateVectorIndexResult(Long id, String vectorIndexStatus, int chunkCount, String errorMessage) {
        mapper.updateVectorIndexResult(id, vectorIndexStatus, chunkCount, errorMessage);
    }

    public void updateGraphIndexStatus(Long id, String graphIndexStatus, String errorMessage) {
        mapper.updateGraphIndexStatus(id, graphIndexStatus, errorMessage);
    }

    public void updateIndexResult(Long id, String indexStatus, int chunkCount, String errorMessage) {
        mapper.updateIndexResult(id, indexStatus, chunkCount, errorMessage);
    }

    public boolean markDeleted(Long id) {
        return mapper.markDeleted(id) > 0;
    }
}
