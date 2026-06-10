package com.hanwha.ai.generation.mapper;

import com.hanwha.ai.generation.domain.GenerationHistory;

public interface GenerationMapper {
    void save(GenerationHistory history);
}
