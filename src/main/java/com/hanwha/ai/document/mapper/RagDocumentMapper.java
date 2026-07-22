package com.hanwha.ai.document.mapper;

import com.hanwha.ai.document.domain.RagDocument;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RagDocumentMapper {
    void insert(RagDocument document);

    List<RagDocument> findAll();

    List<RagDocument> findAllByProjectKey(@Param("projectKey") String projectKey);

    List<RagDocument> findPage(@Param("limit") int limit, @Param("offset") int offset);

    List<RagDocument> findPageByProjectKey(
            @Param("projectKey") String projectKey, @Param("limit") int limit, @Param("offset") int offset);

    long countAll();

    long countByProjectKey(@Param("projectKey") String projectKey);

    boolean projectExists(@Param("projectKey") String projectKey);

    RagDocument findById(@Param("id") Long id);
    RagDocument findActiveByFileHashAndDocumentType(
            @Param("fileHash") String fileHash,
            @Param("documentType") String documentType
    );

    RagDocument findActiveByProjectAndFileHashAndDocumentType(
            @Param("projectKey") String projectKey,
            @Param("fileHash") String fileHash,
            @Param("documentType") String documentType
    );

    RagDocument findByGraphSourceKey(@Param("graphSourceKey") String graphSourceKey);

    void updateIndexMetadata(
            @Param("id") Long id,
            @Param("fileHash") String fileHash,
            @Param("vectorSourceKey") String vectorSourceKey,
            @Param("graphSourceKey") String graphSourceKey
    );

    void updateIndexStatus(
            @Param("id") Long id,
            @Param("indexStatus") String indexStatus,
            @Param("errorMessage") String errorMessage
    );

    void updateVectorIndexStatus(
            @Param("id") Long id,
            @Param("vectorIndexStatus") String vectorIndexStatus,
            @Param("errorMessage") String errorMessage
    );

    void updateVectorIndexResult(
            @Param("id") Long id,
            @Param("vectorIndexStatus") String vectorIndexStatus,
            @Param("chunkCount") int chunkCount,
            @Param("errorMessage") String errorMessage
    );

    void updateGraphIndexStatus(
            @Param("id") Long id,
            @Param("graphIndexStatus") String graphIndexStatus,
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
