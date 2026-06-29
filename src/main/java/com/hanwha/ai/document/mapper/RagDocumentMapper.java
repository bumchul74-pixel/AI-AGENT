package com.hanwha.ai.document.mapper;

import com.hanwha.ai.document.domain.RagDocument;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RagDocumentMapper {
    void insert(RagDocument document);

    List<RagDocument> findAll();

    RagDocument findById(@Param("id") Long id);

    void updateIndexStatus(
            @Param("id") Long id,
            @Param("indexStatus") String indexStatus,
            @Param("errorMessage") String errorMessage
    );

    void updateIndexResult(
            @Param("id") Long id,
            @Param("indexStatus") String indexStatus,
            @Param("chunkCount") int chunkCount,
            @Param("errorMessage") String errorMessage
    );

    int markDeleted(@Param("id") Long id);
}
