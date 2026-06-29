package com.hanwha.ai.generation.controller;

import com.hanwha.ai.generation.config.GenerationProperties;
import com.hanwha.ai.generation.dto.GenerationHistoryResponse;
import com.hanwha.ai.generation.dto.GenerationHistorySearchRequest;
import com.hanwha.ai.generation.dto.GenerationRequest;
import com.hanwha.ai.generation.dto.GenerationResponse;
import com.hanwha.ai.generation.dto.ProjectStructureOptionResponse;
import com.hanwha.ai.generation.service.GenerationService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/history")
    public List<GenerationHistoryResponse> history(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetTypes,
            @RequestParam(required = false) String prompt,
            @RequestParam(required = false) String projectStructure,
            @RequestParam(required = false) String ragDocuments,
            @RequestParam(required = false) String generatedCode,
            @RequestParam(required = false) String llmProvider,
            @RequestParam(required = false) String llmModel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo
    ) {
        GenerationHistorySearchRequest search = new GenerationHistorySearchRequest(
                id,
                clean(targetType),
                clean(targetTypes),
                clean(prompt),
                clean(projectStructure),
                clean(ragDocuments),
                clean(generatedCode),
                clean(llmProvider),
                clean(llmModel),
                startOfDay(createdFrom),
                endExclusive(createdTo)
        );
        return generationService.findHistory(search);
    }

    @GetMapping("/history/{id}")
    public GenerationHistoryResponse historyDetail(@PathVariable Long id) {
        return generationService.findHistoryById(id);
    }

    @PostMapping
    public GenerationResponse generate(@RequestBody GenerationRequest request) {
        return generationService.generate(request);
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private LocalDateTime startOfDay(LocalDate value) {
        return value == null ? null : value.atStartOfDay();
    }

    private LocalDateTime endExclusive(LocalDate value) {
        return value == null ? null : value.plusDays(1).atStartOfDay();
    }
}