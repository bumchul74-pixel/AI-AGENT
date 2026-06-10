package com.hanwha.ai.generation.service;

import com.hanwha.ai.generation.domain.GenerationHistory;
import com.hanwha.ai.generation.dto.GenerationRequest;
import com.hanwha.ai.generation.dto.GenerationResponse;
import com.hanwha.ai.generation.repository.GenerationRepository;
import com.hanwha.ai.global.exception.BusinessException;
import com.hanwha.ai.llm.dto.LlmGenerateRequest;
import com.hanwha.ai.llm.service.LlmClientFactory;
import com.hanwha.ai.rag.config.RagProperties;
import com.hanwha.ai.rag.dto.RagSearchRequest;
import com.hanwha.ai.rag.dto.RagSearchResponse;
import com.hanwha.ai.rag.service.RagClient;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class GenerationServiceImpl implements GenerationService {
    private final RagClient ragClient;
    private final LlmClientFactory llmClientFactory;
    private final GenerationRepository generationRepository;
    private final RagProperties ragProperties;

    public GenerationServiceImpl(
            RagClient ragClient,
            LlmClientFactory llmClientFactory,
            GenerationRepository generationRepository,
            RagProperties ragProperties
    ) {
        this.ragClient = ragClient;
        this.llmClientFactory = llmClientFactory;
        this.generationRepository = generationRepository;
        this.ragProperties = ragProperties;
    }

    @Override
    public GenerationResponse generate(GenerationRequest request) {
        validate(request);

        RagSearchResponse ragSearchResponse = ragClient.search(
                new RagSearchRequest(buildSearchQuery(request), ragProperties.topK())
        );
        List<String> ragDocuments = ragSearchResponse.documents();
        if (ragDocuments.isEmpty()) {
            throw new BusinessException("RAG search result is required before source generation.");
        }

        String context = String.join("\n\n--- RAG SOURCE ---\n\n", ragDocuments);
        String prompt = buildGenerationPrompt(request, context);
        String generatedCode = llmClientFactory.current()
                .generate(new LlmGenerateRequest(prompt, context))
                .content();

        generationRepository.save(new GenerationHistory(
                null,
                request.targetType(),
                request.prompt(),
                generatedCode,
                LocalDateTime.now()
        ));

        return new GenerationResponse(request.targetType(), generatedCode, ragDocuments);
    }

    private void validate(GenerationRequest request) {
        if (request == null
                || !StringUtils.hasText(request.targetType())
                || !StringUtils.hasText(request.prompt())) {
            throw new BusinessException("targetType and prompt are required.");
        }
    }

    private String buildSearchQuery(GenerationRequest request) {
        return """
                Generate Java %s source.
                User request: %s
                Find standard source patterns for Controller, Service, ServiceImpl, Repository, DTO, Mapper, Domain, Exception, and Test Code.
                """.formatted(request.targetType(), request.prompt());
    }

    private String buildGenerationPrompt(GenerationRequest request, String context) {
        return """
                You generate Java source for a Spring Boot project.

                Generation target:
                %s

                User request:
                %s

                Mandatory rules:
                1. First derive the source structure from the retrieved RAG source below.
                2. Prefer package structure, naming style, annotations, method style, DTO shape, mapper/repository style, and exception style found in RAG.
                3. Do not invent a new project pattern when a matching RAG source pattern exists.
                4. If the retrieved source is incomplete, complete only the missing parts needed for a compilable result.
                5. Return only the generated Java source code unless multiple files are required; if multiple files are required, separate them with clear file path headers.

                Retrieved RAG source:
                %s
                """.formatted(request.targetType(), request.prompt(), context);
    }
}
