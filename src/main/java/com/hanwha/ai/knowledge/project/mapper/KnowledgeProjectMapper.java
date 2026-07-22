package com.hanwha.ai.knowledge.project.mapper;

import com.hanwha.ai.knowledge.project.domain.KnowledgeProject;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KnowledgeProjectMapper {
    List<KnowledgeProject> findAll();
    KnowledgeProject findByKey(@Param("projectKey") String projectKey);
    void insert(KnowledgeProject project);
    int update(KnowledgeProject project);
    int deleteIfEmpty(@Param("projectKey") String projectKey);
}
