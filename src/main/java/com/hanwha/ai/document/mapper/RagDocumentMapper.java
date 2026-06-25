package com.hanwha.ai.document.mapper;

import com.hanwha.ai.document.domain.RagDocument;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RagDocumentMapper {
    @Insert("""
            INSERT INTO rag_document (
                original_file_name,
                stored_file_name,
                file_path,
                file_size,
                content_type,
                document_type,
                index_status,
                chunk_count,
                error_message
            ) VALUES (
                #{originalFileName},
                #{storedFileName},
                #{filePath},
                #{fileSize},
                #{contentType},
                #{documentType},
                #{indexStatus},
                #{chunkCount},
                #{errorMessage}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insert(RagDocument document);

    @Select("""
            SELECT
                id,
                original_file_name,
                stored_file_name,
                file_path,
                file_size,
                content_type,
                document_type,
                index_status,
                chunk_count,
                error_message,
                created_at,
                updated_at,
                deleted_at
            FROM rag_document
            WHERE deleted_at IS NULL
            ORDER BY created_at DESC, id DESC
            """)
    @Results(id = "ragDocumentResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "original_file_name", property = "originalFileName"),
            @Result(column = "stored_file_name", property = "storedFileName"),
            @Result(column = "file_path", property = "filePath"),
            @Result(column = "file_size", property = "fileSize"),
            @Result(column = "content_type", property = "contentType"),
            @Result(column = "document_type", property = "documentType"),
            @Result(column = "index_status", property = "indexStatus"),
            @Result(column = "chunk_count", property = "chunkCount"),
            @Result(column = "error_message", property = "errorMessage"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt"),
            @Result(column = "deleted_at", property = "deletedAt")
    })
    List<RagDocument> findAll();

    @Select("""
            SELECT
                id,
                original_file_name,
                stored_file_name,
                file_path,
                file_size,
                content_type,
                document_type,
                index_status,
                chunk_count,
                error_message,
                created_at,
                updated_at,
                deleted_at
            FROM rag_document
            WHERE id = #{id}
              AND deleted_at IS NULL
            """)
    @ResultMap("ragDocumentResultMap")
    RagDocument findById(Long id);

    @Update("""
            UPDATE rag_document
            SET index_status = #{indexStatus},
                error_message = #{errorMessage},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND deleted_at IS NULL
            """)
    void updateIndexStatus(
            @Param("id") Long id,
            @Param("indexStatus") String indexStatus,
            @Param("errorMessage") String errorMessage
    );

    @Update("""
            UPDATE rag_document
            SET index_status = #{indexStatus},
                chunk_count = #{chunkCount},
                error_message = #{errorMessage},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND deleted_at IS NULL
            """)
    void updateIndexResult(
            @Param("id") Long id,
            @Param("indexStatus") String indexStatus,
            @Param("chunkCount") int chunkCount,
            @Param("errorMessage") String errorMessage
    );

    @Update("""
            UPDATE rag_document
            SET index_status = 'DELETED',
                deleted_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND deleted_at IS NULL
            """)
    int markDeleted(Long id);
}
