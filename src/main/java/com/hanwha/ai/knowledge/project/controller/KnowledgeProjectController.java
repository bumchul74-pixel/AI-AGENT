package com.hanwha.ai.knowledge.project.controller;

import com.hanwha.ai.knowledge.project.dto.KnowledgeProjectRequest;
import com.hanwha.ai.knowledge.project.dto.KnowledgeProjectResponse;
import com.hanwha.ai.knowledge.project.service.KnowledgeProjectService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge/projects")
public class KnowledgeProjectController {
    private final KnowledgeProjectService service;

    public KnowledgeProjectController(KnowledgeProjectService service) { this.service = service; }

    @GetMapping
    public List<KnowledgeProjectResponse> findAll() { return service.findAll(); }

    @GetMapping("/{projectKey}")
    public KnowledgeProjectResponse find(@PathVariable String projectKey) { return service.find(projectKey); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeProjectResponse create(@RequestBody KnowledgeProjectRequest request) { return service.create(request); }

    @PutMapping("/{projectKey}")
    public KnowledgeProjectResponse update(@PathVariable String projectKey, @RequestBody KnowledgeProjectRequest request) {
        return service.update(projectKey, request);
    }

    @DeleteMapping("/{projectKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String projectKey) { service.delete(projectKey); }
}
