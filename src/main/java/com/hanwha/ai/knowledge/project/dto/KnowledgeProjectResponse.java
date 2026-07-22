package com.hanwha.ai.knowledge.project.dto;

import com.hanwha.ai.knowledge.project.domain.KnowledgeProject;
import java.time.LocalDateTime;

public record KnowledgeProjectResponse(
        Long id,
        String projectKey,
        String name,
        String description,
        long documentCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static KnowledgeProjectResponse from(KnowledgeProject project) {
        return new KnowledgeProjectResponse(
                project.getId(), project.getProjectKey(), project.getName(), project.getDescription(),
                project.getDocumentCount() == null ? 0 : project.getDocumentCount(),
                project.getCreatedAt(), project.getUpdatedAt()
        );
    }
}
