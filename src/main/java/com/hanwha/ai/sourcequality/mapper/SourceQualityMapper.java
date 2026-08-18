package com.hanwha.ai.sourcequality.mapper;

import com.hanwha.ai.sourcequality.domain.SourceQualitySnapshot;
import com.hanwha.ai.sourcequality.domain.SourceQualityThreshold;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SourceQualityMapper {
    SourceQualityThreshold findThreshold(@Param("projectKey") String projectKey);
    void upsertThreshold(SourceQualityThreshold threshold);
    SourceQualitySnapshot findLatestSnapshot(@Param("projectKey") String projectKey);
    List<SourceQualitySnapshot> findSnapshots(
            @Param("projectKey") String projectKey, @Param("limit") int limit);
    void insertSnapshot(SourceQualitySnapshot snapshot);
}
