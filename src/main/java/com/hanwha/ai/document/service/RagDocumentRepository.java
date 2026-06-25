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
        return findById(document.getId());
    }

    public List<RagDocument> findAll() {
        return mapper.findAll();
    }

    public RagDocument findById(Long id) {
        return mapper.findById(id);
    }

    public void updateIndexStatus(Long id, String indexStatus, String errorMessage) {
        mapper.updateIndexStatus(id, indexStatus, errorMessage);
    }

    public void updateIndexResult(Long id, String indexStatus, int chunkCount, String errorMessage) {
        mapper.updateIndexResult(id, indexStatus, chunkCount, errorMessage);
    }

    public boolean markDeleted(Long id) {
        return mapper.markDeleted(id) > 0;
    }
}
