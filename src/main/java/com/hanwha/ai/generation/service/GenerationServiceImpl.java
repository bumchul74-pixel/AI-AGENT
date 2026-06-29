package com.hanwha.ai.generation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanwha.ai.generation.domain.GenerationHistory;
import com.hanwha.ai.generation.dto.GenerationHistoryResponse;
import com.hanwha.ai.generation.dto.GenerationHistorySearchRequest;
import com.hanwha.ai.generation.dto.GenerationRequest;
import com.hanwha.ai.generation.dto.GenerationResponse;
import com.hanwha.ai.generation.repository.GenerationRepository;
import com.hanwha.ai.global.exception.BusinessException;
import com.hanwha.ai.llm.dto.LlmGenerateRequest;
import com.hanwha.ai.llm.service.LlmClient;
import com.hanwha.ai.llm.service.LlmClientFactory;
import com.hanwha.ai.rag.config.RagProperties;
import com.hanwha.ai.rag.dto.RagSearchRequest;
import com.hanwha.ai.rag.dto.RagSearchResponse;
import com.hanwha.ai.rag.service.RagClient;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class GenerationServiceImpl implements GenerationService {
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final RagClient ragClient;
    private final LlmClientFactory llmClientFactory;
    private final GenerationRepository generationRepository;
    private final RagProperties ragProperties;
    private final ProjectStructureAnalyzer projectStructureAnalyzer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GenerationServiceImpl(
            RagClient ragClient,
            LlmClientFactory llmClientFactory,
            GenerationRepository generationRepository,
            RagProperties ragProperties,
            ProjectStructureAnalyzer projectStructureAnalyzer
    ) {
        this.ragClient = ragClient;
        this.llmClientFactory = llmClientFactory;
        this.generationRepository = generationRepository;
        this.ragProperties = ragProperties;
        this.projectStructureAnalyzer = projectStructureAnalyzer;
    }

    @Override
    @Transactional
    public GenerationResponse generate(GenerationRequest request) {
        validate(request);

        List<String> targetTypes = selectedTargetTypes(request);
        String targetTypesText = targetTypesText(targetTypes);
        String projectPath = request.projectStructure().trim();
        String analyzedProjectStructure = projectStructureAnalyzer.analyze(projectPath, targetTypes);
        RagSearchResponse ragSearchResponse = ragClient.search(
                new RagSearchRequest(
                        buildSearchQuery(request, targetTypesText, projectPath, analyzedProjectStructure),
                        ragProperties.topK()
                )
        );
        List<String> ragDocuments = ragSearchResponse.documents();
        if (ragDocuments.isEmpty()) {
            throw new BusinessException("RAG search result is required before source generation.");
        }

        String ragContext = String.join("\n\n--- RAG SOURCE ---\n\n", ragDocuments);
        String context = buildLlmContext(projectPath, analyzedProjectStructure, ragContext);
        String prompt = buildGenerationPrompt(
                request,
                targetTypes,
                targetTypesText,
                projectPath,
                analyzedProjectStructure,
                ragContext
        );
        LlmClient llmClient = llmClientFactory.current();
        String generatedCode = llmClient.generate(new LlmGenerateRequest(prompt, context)).content();

        GenerationHistory savedHistory = generationRepository.save(createHistory(
                targetTypesText,
                targetTypes,
                request.prompt(),
                analyzedProjectStructure,
                ragDocuments,
                generatedCode,
                llmClient.provider().name()
        ));
        Long historyId = savedHistory == null ? null : savedHistory.getId();

        return new GenerationResponse(targetTypesText, targetTypes, generatedCode, ragDocuments, analyzedProjectStructure, historyId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GenerationHistoryResponse> findHistory(GenerationHistorySearchRequest search) {
        return generationRepository.findAll(search).stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public GenerationHistoryResponse findHistoryById(Long id) {
        GenerationHistory history = generationRepository.findById(id);
        if (history == null) {
            throw new BusinessException("Generation history not found.");
        }

        return toHistoryResponse(history);
    }

    private GenerationHistory createHistory(
            String targetTypesText,
            List<String> targetTypes,
            String prompt,
            String projectStructure,
            List<String> ragDocuments,
            String generatedCode,
            String llmProvider
    ) {
        GenerationHistory history = new GenerationHistory();
        history.setTargetType(targetTypesText);
        history.setTargetTypesJson(toJson(targetTypes));
        history.setPrompt(prompt);
        history.setProjectStructure(projectStructure);
        history.setRagDocumentsJson(toJson(ragDocuments));
        history.setGeneratedCode(generatedCode);
        history.setLlmProvider(llmProvider);
        history.setLlmModel(null);
        return history;
    }

    private GenerationHistoryResponse toHistoryResponse(GenerationHistory history) {
        return new GenerationHistoryResponse(
                history.getId(),
                history.getTargetType(),
                readJsonList(history.getTargetTypesJson()),
                history.getPrompt(),
                history.getProjectStructure(),
                readJsonList(history.getRagDocumentsJson()),
                history.getGeneratedCode(),
                history.getLlmProvider(),
                history.getLlmModel(),
                history.getCreatedAt()
        );
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("Failed to serialize generation history.");
        }
    }

    private List<String> readJsonList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }

        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private void validate(GenerationRequest request) {
        if (request == null
                || selectedTargetTypes(request).isEmpty()
                || !StringUtils.hasText(request.prompt())
                || !StringUtils.hasText(request.projectStructure())) {
            throw new BusinessException("targetTypes, prompt, and projectStructure are required.");
        }
    }

    private List<String> selectedTargetTypes(GenerationRequest request) {
        if (request == null || request.targetTypes() == null) {
            return List.of();
        }

        LinkedHashSet<String> selectedTargetTypes = new LinkedHashSet<>();
        for (String targetType : request.targetTypes()) {
            if (StringUtils.hasText(targetType)) {
                selectedTargetTypes.add(targetType.trim());
            }
        }
        return List.copyOf(selectedTargetTypes);
    }

    private String targetTypesText(List<String> targetTypes) {
        return String.join(", ", targetTypes);
    }

    private String buildSearchQuery(
            GenerationRequest request,
            String targetTypesText,
            String projectPath,
            String analyzedProjectStructure
    ) {
        return """
                Generate Java source only for these selected target types: %s.
                User request: %s
                Selected project full path: %s
                MCP analyzed project structure:
                %s
                Find standard source patterns for the selected target types only. Use other layers only as reference context; do not search for unselected or full-stack generation patterns.
                """.formatted(targetTypesText, request.prompt(), projectPath, analyzedProjectStructure);
    }

    private String buildLlmContext(String projectPath, String analyzedProjectStructure, String ragContext) {
        return """
                Selected project full path:
                %s

                MCP analyzed project structure:
                %s

                Retrieved RAG source:
                %s
                """.formatted(projectPath, analyzedProjectStructure, ragContext);
    }

    private String buildGenerationPrompt(
            GenerationRequest request,
            List<String> targetTypes,
            String targetTypesText,
            String projectPath,
            String analyzedProjectStructure,
            String ragContext
    ) {
        return """
                You generate Java source for a Spring Boot project.

                Selected generation target types:
                %s

                User request:
                %s

                Mandatory rules:
                1. Generate source only for the selected target types: %s.
                2. Do not output Java source for any unselected target type. For example, when Controller and DTO are selected, return Controller and DTO code only and do not generate Service, ServiceImpl, Repository, Mapper, Domain, Exception, or Test Code files.
                3. If the user request mentions unselected target types, treat them only as dependency or design context and do not include their source code in the output.
                4. Use the MCP analyzed project structure and selected project full path to choose package, module, layer, and file path only for selected target types.
                5. Use the retrieved RAG source as the primary code pattern for annotations, method style, dependency style, and exception style.
                6. If retrieved source is incomplete, complete only the missing parts needed for compilable selected-target results.
                7. %s

                Selected project full path:
                %s

                MCP analyzed project structure:
                %s

                Retrieved RAG source:
                %s
                """.formatted(
                targetTypesText,
                request.prompt(),
                targetTypesText,
                outputFormatRule(targetTypes),
                projectPath,
                analyzedProjectStructure,
                ragContext
        );
    }

    private String outputFormatRule(List<String> targetTypes) {
        if (targetTypes.size() == 1) {
            return "Return only one Java source code output for the selected target type. Do not include multiple file path headers.";
        }
        return "Return one clearly separated Java source section for each selected target type, and do not include sections for unselected target types.";
    }
}
