package com.hanwha.ai.generation.controller;

import com.hanwha.ai.generation.config.GenerationProperties;
import com.hanwha.ai.generation.dto.GenerationRequest;
import com.hanwha.ai.generation.dto.GenerationResponse;
import com.hanwha.ai.generation.dto.ProjectStructureOptionResponse;
import com.hanwha.ai.generation.service.GenerationService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/generations")
public class GenerationController {
    private final GenerationService generationService;
    private final GenerationProperties generationProperties;

    public GenerationController(GenerationService generationService, GenerationProperties generationProperties) {
        this.generationService = generationService;
        this.generationProperties = generationProperties;
    }

    @GetMapping("/project-structures")
    public List<ProjectStructureOptionResponse> projectStructures() {
        return generationProperties.projectStructures().stream()
                .map(projectStructure -> new ProjectStructureOptionResponse(
                        projectStructure.name(),
                        projectStructure.value()
                ))
                .toList();
    }

    @PostMapping
    public GenerationResponse generate(@RequestBody GenerationRequest request) {
        return generationService.generate(request);
    }
}