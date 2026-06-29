package com.hanwha.ai.generation.repository;

import com.hanwha.ai.generation.domain.GenerationHistory;
import com.hanwha.ai.generation.dto.GenerationHistorySearchRequest;
import com.hanwha.ai.generation.mapper.GenerationMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class GenerationRepository {
    private final GenerationMapper mapper;

    public GenerationRepository() {
        this.mapper = null;
    }

    @Autowired
    public GenerationRepository(GenerationMapper mapper) {
        this.mapper = mapper;
    }

    public GenerationHistory save(GenerationHistory history) {
        if (mapper == null) {
            return history;
        }

        mapper.insert(history);
        return findById(history.getId());
    }

    public List<GenerationHistory> findAll() {
        return findAll(GenerationHistorySearchRequest.empty());
    }

    public List<GenerationHistory> findAll(GenerationHistorySearchRequest search) {
        if (mapper == null) {
            return List.of();
        }

        return mapper.findAll(search);
    }

    public GenerationHistory findById(Long id) {
        if (mapper == null) {
            return null;
        }

        return mapper.findById(id);
    }
}