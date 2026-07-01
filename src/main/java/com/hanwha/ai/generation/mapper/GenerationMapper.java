package com.hanwha.ai.generation.mapper;

import com.hanwha.ai.generation.domain.GenerationHistory;
import com.hanwha.ai.generation.dto.GenerationHistorySearchRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GenerationMapper {
    void insert(GenerationHistory history);

    List<GenerationHistory> findAll(@Param("search") GenerationHistorySearchRequest search);

    GenerationHistory findById(@Param("id") Long id);

    void updateNeo4jIndexStatus(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("indexedAt") LocalDateTime indexedAt,
            @Param("error") String error
    );
}