package com.hanwha.ai.generation.repository;

import com.hanwha.ai.generation.domain.GenerationHistory;
import org.springframework.stereotype.Repository;

@Repository
public class GenerationRepository {
    public void save(GenerationHistory history) {
        // MyBatis mapper integration will be connected when DB schema is defined.
    }
}
