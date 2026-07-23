package com.hanwha.ai.securecoding.mapper;

import com.hanwha.ai.securecoding.domain.SecureCodingScanFile;
import com.hanwha.ai.securecoding.domain.SecureCodingScanJob;
import com.hanwha.ai.securecoding.dto.SecureCodingResultRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SecureCodingScanMapper {
    Long insertJob(@Param("projectKey") String projectKey, @Param("totalFiles") int totalFiles);
    void insertFile(@Param("jobId") Long jobId, @Param("documentId") Long documentId,
                    @Param("fileName") String fileName, @Param("fileType") String fileType);
    SecureCodingScanJob findJobById(@Param("jobId") Long jobId);
    SecureCodingScanJob findLatestByProject(@Param("projectKey") String projectKey);
    SecureCodingScanJob findActiveByProject(@Param("projectKey") String projectKey);
    List<SecureCodingScanJob> findRecoverableJobs();
    List<SecureCodingScanFile> findPendingFiles(@Param("jobId") Long jobId);
    List<SecureCodingResultRow> findResults(@Param("jobId") Long jobId);
    int markJobRunning(@Param("jobId") Long jobId);
    int markFileRunning(@Param("fileId") Long fileId);
    void insertResult(@Param("jobId") Long jobId, @Param("fileId") Long fileId,
                      @Param("row") SecureCodingResultRow row);
    int completeFile(@Param("fileId") Long fileId, @Param("status") String status,
                     @Param("errorMessage") String errorMessage);
    int incrementProgress(@Param("jobId") Long jobId, @Param("passed") int passed,
                          @Param("findings") int findings, @Param("errors") int errors);
    int finalizeJob(@Param("jobId") Long jobId);
    int failJob(@Param("jobId") Long jobId, @Param("errorMessage") String errorMessage);
    int resetInterruptedFiles(@Param("jobId") Long jobId);
}
