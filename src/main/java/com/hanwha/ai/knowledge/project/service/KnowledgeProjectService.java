package com.hanwha.ai.knowledge.project.service;

import com.hanwha.ai.global.exception.BusinessException;
import com.hanwha.ai.knowledge.project.domain.KnowledgeProject;
import com.hanwha.ai.knowledge.project.dto.KnowledgeProjectRequest;
import com.hanwha.ai.knowledge.project.dto.KnowledgeProjectResponse;
import com.hanwha.ai.knowledge.project.mapper.KnowledgeProjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeProjectService {
    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_-]{1,63}");
    private final KnowledgeProjectMapper mapper;

    public KnowledgeProjectService(KnowledgeProjectMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeProjectResponse> findAll() {
        return mapper.findAll().stream().map(KnowledgeProjectResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public KnowledgeProjectResponse find(String projectKey) {
        return KnowledgeProjectResponse.from(require(projectKey));
    }

    @Transactional
    public KnowledgeProjectResponse create(KnowledgeProjectRequest request) {
        if (request == null) throw new BusinessException("Project request is required.");
        String projectKey = normalizeKey(request == null ? null : request.projectKey());
        String name = requireName(request == null ? null : request.name());
        if (mapper.findByKey(projectKey) != null) {
            throw new BusinessException("Project key already exists: " + projectKey);
        }
        KnowledgeProject project = new KnowledgeProject();
        project.setProjectKey(projectKey);
        project.setName(name);
        project.setDescription(trimToNull(request.description(), 500));
        mapper.insert(project);
        return KnowledgeProjectResponse.from(require(projectKey));
    }

    @Transactional
    public KnowledgeProjectResponse update(String projectKey, KnowledgeProjectRequest request) {
        if (request == null) throw new BusinessException("Project request is required.");
        KnowledgeProject project = require(projectKey);
        project.setName(requireName(request == null ? null : request.name()));
        project.setDescription(trimToNull(request.description(), 500));
        mapper.update(project);
        return KnowledgeProjectResponse.from(require(project.getProjectKey()));
    }

    @Transactional
    public void delete(String projectKey) {
        KnowledgeProject project = require(projectKey);
        if (project.getDocumentCount() != null && project.getDocumentCount() > 0) {
            throw new BusinessException("Delete indexed documents before deleting the project.");
        }
        if (mapper.deleteIfEmpty(project.getProjectKey()) == 0) {
            throw new BusinessException("The default project cannot be deleted.");
        }
    }

    private KnowledgeProject require(String projectKey) {
        KnowledgeProject project = mapper.findByKey(normalizeKey(projectKey));
        if (project == null) throw new BusinessException("Project not found.");
        return project;
    }

    private String normalizeKey(String value) {
        String key = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new BusinessException("Project key must be 2-64 lowercase letters, numbers, '_' or '-'.");
        }
        return key;
    }

    private String requireName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty() || name.length() > 120) {
            throw new BusinessException("Project name must be between 1 and 120 characters.");
        }
        return name;
    }

    private String trimToNull(String value, int maxLength) {
        if (value == null || value.trim().isEmpty()) return null;
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) throw new BusinessException("Description is too long.");
        return trimmed;
    }
}
