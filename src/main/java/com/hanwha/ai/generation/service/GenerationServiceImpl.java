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
import com.hanwha.ai.knowledge.project.dto.KnowledgeProjectResponse;
import com.hanwha.ai.knowledge.project.service.KnowledgeProjectService;
import com.hanwha.ai.rag.config.RagProperties;
import com.hanwha.ai.rag.dto.RagSearchRequest;
import com.hanwha.ai.rag.dto.RagSearchResponse;
import com.hanwha.ai.rag.dto.HybridSearchResult;
import com.hanwha.ai.rag.service.HybridSearchService;
import com.hanwha.ai.rag.service.RagClient;
import com.hanwha.ai.sourcegraph.dto.SourceGraphIndexResult;
import com.hanwha.ai.sourcegraph.dto.SourceGraphReindexResponse;
import com.hanwha.ai.sourcegraph.dto.SourceGraphResponse;
import com.hanwha.ai.sourcegraph.service.NoOpSourceGraphService;
import com.hanwha.ai.sourcegraph.service.SourceGraphService;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Set;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class GenerationServiceImpl implements GenerationService {
    private static final Logger log = LoggerFactory.getLogger(GenerationServiceImpl.class);
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final int MAPPER_COLUMN_VALIDATION_MAX_ATTEMPTS = 2;
    private static final Set<String> MAPPER_TARGET_TYPES = Set.of("mapper");
    private static final Set<String> SQL_COLUMN_VALIDATION_IGNORE = Set.of(
            "select", "from", "where", "and", "or", "as", "distinct", "count", "sum", "min", "max", "avg",
            "case", "when", "then", "else", "end", "null", "current_timestamp", "current_date", "current_time",
            "true", "false", "now", "values", "set", "update", "insert", "into", "delete", "order", "by",
            "group", "having", "limit", "offset", "desc", "asc", "on", "join", "left", "right", "inner", "outer"
    );
    private static final Pattern XML_COLUMN_ATTRIBUTE_PATTERN = Pattern.compile(
            "\\bcolumn\\s*=\\s*[\\\"']([A-Za-z_][A-Za-z0-9_]*)[\\\"']",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern INSERT_COLUMNS_PATTERN = Pattern.compile(
            "(?is)\\binsert\\s+into\\s+[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)?\\s*\\((.*?)\\)"
    );
    private static final Pattern UPDATE_SET_PATTERN = Pattern.compile(
            "(?is)\\bupdate\\s+[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)?\\s+set\\s+(.*?)(?:\\bwhere\\b|</update>|$)"
    );
    private static final Pattern SELECT_COLUMNS_PATTERN = Pattern.compile(
            "(?is)\\bselect\\s+(.*?)\\s+from\\s+[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)?"
    );
    private static final Pattern SQL_FRAGMENT_PATTERN = Pattern.compile(
            "(?is)<sql\\b[^>]*>(.*?)</sql>"
    );

    private final RagClient ragClient;
    private final LlmClientFactory llmClientFactory;
    private final GenerationRepository generationRepository;
    private final RagProperties ragProperties;
    private final ProjectStructureAnalyzer projectStructureAnalyzer;
    private final SourceGraphService sourceGraphService;
    private final DatabaseSchemaContextProvider databaseSchemaContextProvider;
    private final HybridSearchService hybridSearchService;
    private final KnowledgeProjectService knowledgeProjectService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GenerationServiceImpl(
            RagClient ragClient,
            LlmClientFactory llmClientFactory,
            GenerationRepository generationRepository,
            RagProperties ragProperties,
            ProjectStructureAnalyzer projectStructureAnalyzer
    ) {
        this(
                ragClient,
                llmClientFactory,
                generationRepository,
                ragProperties,
                projectStructureAnalyzer,
                NoOpSourceGraphService.INSTANCE,
                NoOpDatabaseSchemaContextProvider.INSTANCE
        );
    }

    public GenerationServiceImpl(
            RagClient ragClient,
            LlmClientFactory llmClientFactory,
            GenerationRepository generationRepository,
            RagProperties ragProperties,
            ProjectStructureAnalyzer projectStructureAnalyzer,
            SourceGraphService sourceGraphService
    ) {
        this(
                ragClient,
                llmClientFactory,
                generationRepository,
                ragProperties,
                projectStructureAnalyzer,
                sourceGraphService,
                NoOpDatabaseSchemaContextProvider.INSTANCE
        );
    }

    public GenerationServiceImpl(
            RagClient ragClient,
            LlmClientFactory llmClientFactory,
            GenerationRepository generationRepository,
            RagProperties ragProperties,
            ProjectStructureAnalyzer projectStructureAnalyzer,
            SourceGraphService sourceGraphService,
            DatabaseSchemaContextProvider databaseSchemaContextProvider
    ) {
        this(ragClient, llmClientFactory, generationRepository, ragProperties, projectStructureAnalyzer,
                sourceGraphService, databaseSchemaContextProvider,
                new HybridSearchService(ragClient, sourceGraphService));
    }

    public GenerationServiceImpl(
            RagClient ragClient,
            LlmClientFactory llmClientFactory,
            GenerationRepository generationRepository,
            RagProperties ragProperties,
            ProjectStructureAnalyzer projectStructureAnalyzer,
            SourceGraphService sourceGraphService,
            DatabaseSchemaContextProvider databaseSchemaContextProvider,
            HybridSearchService hybridSearchService
    ) {
        this(ragClient, llmClientFactory, generationRepository, ragProperties, projectStructureAnalyzer,
                sourceGraphService, databaseSchemaContextProvider, hybridSearchService, null);
    }

    @Autowired
    public GenerationServiceImpl(
            RagClient ragClient,
            LlmClientFactory llmClientFactory,
            GenerationRepository generationRepository,
            RagProperties ragProperties,
            ProjectStructureAnalyzer projectStructureAnalyzer,
            SourceGraphService sourceGraphService,
            DatabaseSchemaContextProvider databaseSchemaContextProvider,
            HybridSearchService hybridSearchService,
            KnowledgeProjectService knowledgeProjectService
    ) {
        this.ragClient = ragClient;
        this.llmClientFactory = llmClientFactory;
        this.generationRepository = generationRepository;
        this.ragProperties = ragProperties;
        this.projectStructureAnalyzer = projectStructureAnalyzer;
        this.sourceGraphService = sourceGraphService;
        this.databaseSchemaContextProvider = databaseSchemaContextProvider;
        this.hybridSearchService = hybridSearchService;
        this.knowledgeProjectService = knowledgeProjectService;
    }

    @Override
    @Transactional
    public GenerationResponse generate(GenerationRequest request) {
        validate(request);

        List<String> targetTypes = selectedTargetTypes(request);
        String targetTypesText = targetTypesText(targetTypes);
        KnowledgeProjectResponse selectedProject = selectedProject(request);
        String projectKey = selectedProject == null ? null : selectedProject.projectKey();
        String projectReference = selectedProject == null
                ? request.projectStructure().trim()
                : selectedProject.name() + " (" + selectedProject.projectKey() + ")";
        String analyzedProjectStructure = selectedProject == null
                ? projectStructureAnalyzer.analyze(projectReference, targetTypes)
                : knowledgeProjectContext(selectedProject);
        DatabaseSchemaContext databaseSchemaContext = databaseSchemaContextProvider.resolve(request, targetTypes);
        HybridSearchResult hybridSearchResult = hybridSearchService.search(
                new RagSearchRequest(
                        buildSearchQuery(request, targetTypesText, projectReference, analyzedProjectStructure, databaseSchemaContext),
                        ragProperties.topK(),
                        projectKey
                )
        );
        List<String> ragDocuments = hybridSearchResult.documents();
        if (ragDocuments.isEmpty()) {
            throw new BusinessException("RAG search result is required before source generation.");
        }

        String ragContext = hybridSearchResult.context();
        String context = buildLlmContext(projectReference, analyzedProjectStructure, ragContext, databaseSchemaContext);
        String prompt = buildGenerationPrompt(
                request,
                targetTypes,
                targetTypesText,
                projectReference,
                analyzedProjectStructure,
                ragContext,
                databaseSchemaContext
        );
        LlmClient llmClient = llmClientFactory.current();
        String generatedCode = generateWithMapperColumnValidation(llmClient, prompt, context, targetTypes, databaseSchemaContext);

        GenerationHistory savedHistory = generationRepository.save(createHistory(
                targetTypesText,
                targetTypes,
                request.prompt(),
                projectKey,
                analyzedProjectStructure,
                ragDocuments,
                generatedCode,
                llmClient.provider().name()
        ));
        Long historyId = savedHistory == null ? null : savedHistory.getId();
        indexHistoryGraph(savedHistory);

        return new GenerationResponse(
                targetTypesText,
                targetTypes,
                generatedCode,
                ragDocuments,
                analyzedProjectStructure,
                historyId,
                mcpContextApplied(databaseSchemaContext, analyzedProjectStructure),
                mcpContextMessage(databaseSchemaContext, analyzedProjectStructure),
                projectKey
        );
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
        return toHistoryResponse(requiredHistory(id));
    }

    @Override
    @Transactional(readOnly = true)
    public SourceGraphResponse findHistoryGraph(Long id) {
        requiredHistory(id);
        return sourceGraphService.findByHistoryId(id);
    }

    @Override
    @Transactional
    public SourceGraphReindexResponse reindexHistoryGraph(Long id) {
        GenerationHistory history = requiredHistory(id);
        SourceGraphIndexResult indexResult = indexHistoryGraph(history);
        SourceGraphResponse graph = sourceGraphService.findByHistoryId(id);
        return new SourceGraphReindexResponse(indexResult, graph);
    }

    private GenerationHistory requiredHistory(Long id) {
        GenerationHistory history = generationRepository.findById(id);
        if (history == null) {
            throw new BusinessException("Generation history not found.");
        }
        return history;
    }

    private SourceGraphIndexResult indexHistoryGraph(GenerationHistory history) {
        if (history == null || history.getId() == null) {
            return null;
        }

        SourceGraphIndexResult indexResult;
        try {
            indexResult = sourceGraphService.index(history);
        } catch (Exception exception) {
            log.warn("Unexpected source graph indexing error for generation history {}.", history.getId(), exception);
            indexResult = SourceGraphIndexResult.failed(LocalDateTime.now(), abbreviate(exception.getMessage()));
        }
        generationRepository.updateNeo4jIndexStatus(
                history.getId(),
                indexResult.status().name(),
                indexResult.indexedAt(),
                indexResult.errorMessage()
        );
        history.setNeo4jIndexStatus(indexResult.status().name());
        history.setNeo4jIndexedAt(indexResult.indexedAt());
        history.setNeo4jIndexError(indexResult.errorMessage());
        return indexResult;
    }

    private GenerationHistory createHistory(
            String targetTypesText,
            List<String> targetTypes,
            String prompt,
            String projectKey,
            String projectStructure,
            List<String> ragDocuments,
            String generatedCode,
            String llmProvider
    ) {
        GenerationHistory history = new GenerationHistory();
        history.setTargetType(targetTypesText);
        history.setTargetTypesJson(toJson(targetTypes));
        history.setPrompt(prompt);
        history.setProjectKey(projectKey);
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
                history.getProjectKey(),
                history.getProjectStructure(),
                readJsonList(history.getRagDocumentsJson()),
                history.getGeneratedCode(),
                history.getLlmProvider(),
                history.getLlmModel(),
                history.getNeo4jIndexStatus(),
                history.getNeo4jIndexedAt(),
                history.getNeo4jIndexError(),
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
                || (!StringUtils.hasText(request.projectKey()) && !StringUtils.hasText(request.projectStructure()))) {
            String projectField = request != null && request.projectKey() != null
                    ? "projectKey"
                    : "projectStructure";
            throw new BusinessException("targetTypes, prompt, and " + projectField + " are required.");
        }
    }

    private KnowledgeProjectResponse selectedProject(GenerationRequest request) {
        if (!StringUtils.hasText(request.projectKey())) {
            return null;
        }
        if (knowledgeProjectService == null) {
            throw new BusinessException("Knowledge project service is unavailable.");
        }
        return knowledgeProjectService.find(request.projectKey());
    }

    private String knowledgeProjectContext(KnowledgeProjectResponse project) {
        return """
                Knowledge project metadata:
                Project key: %s
                Project name: %s
                Description: %s
                Indexed document count: %d
                Use only this project's indexed source, documents, and ontology as generation evidence.
                """.formatted(
                project.projectKey(),
                project.name(),
                StringUtils.hasText(project.description()) ? project.description() : "No description",
                project.documentCount()
        );
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
            String projectReference,
            String analyzedProjectStructure,
            DatabaseSchemaContext databaseSchemaContext
    ) {
        return """
                Generate Java source only for these selected target types: %s.
                User request: %s
                Selected project (Selected project full path for legacy requests): %s
                Selected project context (MCP analyzed project structure for legacy requests):
                %s
                %s
                Find standard source patterns for the selected target types only. Use other layers only as reference context; do not search for unselected or full-stack generation patterns.
                """.formatted(
                targetTypesText,
                request.prompt(),
                projectReference,
                analyzedProjectStructure,
                databaseSchemaContextText(databaseSchemaContext)
        );
    }

    private String buildLlmContext(
            String projectReference,
            String analyzedProjectStructure,
            String ragContext,
            DatabaseSchemaContext databaseSchemaContext
    ) {
        return """
                Selected project (Selected project full path for legacy requests):
                %s

                Selected project context (MCP analyzed project structure for legacy requests):
                %s

                %s

                Retrieved RAG source:
                %s
                """.formatted(projectReference, analyzedProjectStructure, databaseSchemaContextText(databaseSchemaContext), ragContext);
    }

    private String buildGenerationPrompt(
            GenerationRequest request,
            List<String> targetTypes,
            String targetTypesText,
            String projectReference,
            String analyzedProjectStructure,
            String ragContext,
            DatabaseSchemaContext databaseSchemaContext
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
                4. Use the MCP analyzed project structure and selected project full path for legacy requests, or the selected project metadata, indexed sources, and ontology for project-managed requests, to choose package, module, layer, and file path only for selected target types.
                5. For Mapper, DTO, or DOMAIN selected targets, if MCP database schema context contains matched table metadata, use that DB context before RAG for table names, columns, Java field names, Java types, keys, indexes, comments, and MyBatis SQL.
                6. If generate_mybatis_mapper output is present in MCP database schema context, adapt that SQL and MyBatis mapping instead of inventing Mapper SQL.
                7. If MCP database schema context is unavailable or no matching DB table was found, use the retrieved RAG source for table and field information and do not invent columns.
                8. Use the retrieved RAG source as the primary code pattern for annotations, method style, dependency style, and exception style.
                9. If retrieved source is incomplete, complete only the missing parts needed for compilable selected-target results.
                10. %s

                Selected project (Selected project full path for legacy requests):
                %s

                Selected project context (MCP analyzed project structure for legacy requests):
                %s

                %s

                Retrieved RAG source:
                %s
                """.formatted(
                targetTypesText,
                request.prompt(),
                targetTypesText,
                outputFormatRule(targetTypes),
                projectReference,
                analyzedProjectStructure,
                databaseSchemaContextText(databaseSchemaContext),
                ragContext
        );
    }

    private String generateWithMapperColumnValidation(
            LlmClient llmClient,
            String prompt,
            String context,
            List<String> targetTypes,
            DatabaseSchemaContext databaseSchemaContext
    ) {
        LlmGenerateRequest llmRequest = new LlmGenerateRequest(prompt, context);
        List<String> lastInvalidColumns = List.of();

        for (int attempt = 1; attempt <= MAPPER_COLUMN_VALIDATION_MAX_ATTEMPTS; attempt++) {
            String generatedCode = llmClient.generate(llmRequest).content();
            lastInvalidColumns = invalidMapperColumns(generatedCode, targetTypes, databaseSchemaContext);
            if (lastInvalidColumns.isEmpty()) {
                return generatedCode;
            }

            if (attempt < MAPPER_COLUMN_VALIDATION_MAX_ATTEMPTS) {
                llmRequest = new LlmGenerateRequest(
                        prompt + "\n\n" + mapperColumnRegenerationInstruction(lastInvalidColumns),
                        context
                );
            }
        }

        throw new BusinessException(
                "Generated Mapper XML contains columns that do not exist in the selected database table metadata: "
                        + String.join(", ", lastInvalidColumns)
        );
    }

    private String mapperColumnRegenerationInstruction(List<String> invalidColumns) {
        return """
                Previous Mapper XML validation failed because it referenced columns that are not present in MCP describe_database_table_columns metadata.
                Invalid columns: %s
                Regenerate the selected Mapper XML using only the allowed columns listed in MCP database schema context.
                Do not include any resultMap column, SELECT column, INSERT column, or UPDATE SET column outside the allowed DB column list.
                """.formatted(String.join(", ", invalidColumns));
    }

    private List<String> invalidMapperColumns(
            String generatedCode,
            List<String> targetTypes,
            DatabaseSchemaContext databaseSchemaContext
    ) {
        if (!requiresMapperColumnValidation(targetTypes, databaseSchemaContext)) {
            return List.of();
        }

        Set<String> allowedColumns = allowedMapperColumns(databaseSchemaContext);
        if (allowedColumns.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> invalidColumns = new LinkedHashSet<>();
        for (String column : extractGeneratedMapperColumns(generatedCode)) {
            String normalizedColumn = column.toLowerCase(Locale.ROOT);
            if (!allowedColumns.contains(normalizedColumn)) {
                invalidColumns.add(column);
            }
        }
        return List.copyOf(invalidColumns);
    }

    private boolean requiresMapperColumnValidation(List<String> targetTypes, DatabaseSchemaContext databaseSchemaContext) {
        return databaseSchemaContext != null
                && databaseSchemaContext.matchedTables()
                && databaseSchemaContext.hasColumnMetadata()
                && targetTypes != null
                && targetTypes.stream()
                .filter(StringUtils::hasText)
                .map(targetType -> targetType.trim().toLowerCase(Locale.ROOT))
                .anyMatch(MAPPER_TARGET_TYPES::contains);
    }

    private Set<String> allowedMapperColumns(DatabaseSchemaContext databaseSchemaContext) {
        LinkedHashSet<String> allowedColumns = new LinkedHashSet<>();
        for (List<String> columns : databaseSchemaContext.tableColumns().values()) {
            for (String column : columns) {
                if (StringUtils.hasText(column)) {
                    allowedColumns.add(column.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return Set.copyOf(allowedColumns);
    }

    private List<String> extractGeneratedMapperColumns(String generatedCode) {
        if (!StringUtils.hasText(generatedCode)) {
            return List.of();
        }

        LinkedHashSet<String> columns = new LinkedHashSet<>();
        addXmlColumnAttributes(columns, generatedCode);
        addSqlFragmentColumns(columns, generatedCode);
        addInsertColumns(columns, generatedCode);
        addUpdateColumns(columns, generatedCode);
        addSelectColumns(columns, generatedCode);
        return List.copyOf(columns);
    }

    private void addXmlColumnAttributes(LinkedHashSet<String> columns, String generatedCode) {
        Matcher matcher = XML_COLUMN_ATTRIBUTE_PATTERN.matcher(generatedCode);
        while (matcher.find()) {
            addGeneratedColumn(columns, matcher.group(1));
        }
    }

    private void addSqlFragmentColumns(LinkedHashSet<String> columns, String generatedCode) {
        Matcher matcher = SQL_FRAGMENT_PATTERN.matcher(generatedCode);
        while (matcher.find()) {
            addDelimitedColumns(columns, matcher.group(1));
        }
    }
    private void addInsertColumns(LinkedHashSet<String> columns, String generatedCode) {
        Matcher matcher = INSERT_COLUMNS_PATTERN.matcher(generatedCode);
        while (matcher.find()) {
            addDelimitedColumns(columns, matcher.group(1));
        }
    }

    private void addUpdateColumns(LinkedHashSet<String> columns, String generatedCode) {
        Matcher matcher = UPDATE_SET_PATTERN.matcher(generatedCode);
        while (matcher.find()) {
            for (String assignment : matcher.group(1).split(",")) {
                int equalsIndex = assignment.indexOf('=');
                if (equalsIndex > 0) {
                    addGeneratedColumn(columns, assignment.substring(0, equalsIndex));
                }
            }
        }
    }

    private void addSelectColumns(LinkedHashSet<String> columns, String generatedCode) {
        Matcher matcher = SELECT_COLUMNS_PATTERN.matcher(generatedCode);
        while (matcher.find()) {
            addDelimitedColumns(columns, matcher.group(1));
        }
    }

    private void addDelimitedColumns(LinkedHashSet<String> columns, String value) {
        for (String candidate : value.split("[,\\r\\n]+")) {
            addGeneratedColumn(columns, candidate);
        }
    }

    private void addGeneratedColumn(LinkedHashSet<String> columns, String candidate) {
        String column = cleanGeneratedColumn(candidate);
        if (StringUtils.hasText(column)) {
            columns.add(column);
        }
    }

    private String cleanGeneratedColumn(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return "";
        }

        String cleaned = candidate.trim();
        if (cleaned.startsWith("<") || cleaned.contains("#{") || cleaned.contains("${") || cleaned.contains("(")) {
            return "";
        }

        cleaned = cleaned.replaceAll("(?i)\\s+AS\\s+[A-Za-z_][A-Za-z0-9_]*", "");
        String[] tokens = cleaned.trim().split("\\s+");
        cleaned = tokens.length == 0 ? "" : tokens[0];
        cleaned = cleaned
                .replace("`", "")
                .replace("\"", "")
                .replace("'", "")
                .replace("[", "")
                .replace("]", "")
                .replaceFirst("^[^A-Za-z_]+", "")
                .replaceFirst("[^A-Za-z0-9_]+$", "");
        if (cleaned.contains(".")) {
            cleaned = cleaned.substring(cleaned.lastIndexOf('.') + 1);
        }

        String normalizedColumn = cleaned.toLowerCase(Locale.ROOT);
        if (!cleaned.matches("[A-Za-z_][A-Za-z0-9_]*") || SQL_COLUMN_VALIDATION_IGNORE.contains(normalizedColumn)) {
            return "";
        }
        return cleaned;
    }
    private boolean mcpContextApplied(DatabaseSchemaContext databaseSchemaContext, String analyzedProjectStructure) {
        return hasProjectStructureMcp(analyzedProjectStructure)
                || hasAppliedDatabaseContext(databaseSchemaContext)
                || hasDatabaseContextDiagnostics(databaseSchemaContext);
    }

    private String mcpContextMessage(DatabaseSchemaContext databaseSchemaContext, String analyzedProjectStructure) {
        boolean projectStructureMcp = hasProjectStructureMcp(analyzedProjectStructure);
        boolean databaseContextApplied = hasAppliedDatabaseContext(databaseSchemaContext);

        if (databaseContextApplied) {
            List<String> appliedTools = mcpAppliedTools(databaseSchemaContext, analyzedProjectStructure);
            String tableText = databaseSchemaContext.tableNames().isEmpty()
                    ? ""
                    : " 대상 테이블: " + String.join(", ", databaseSchemaContext.tableNames()) + ".";
            String toolText = appliedTools.isEmpty()
                    ? ""
                    : " 적용 Tool: " + String.join(", ", appliedTools) + ".";

            return "MCP 결과가 생성 결과에 반영되었습니다." + tableText + toolText;
        }

        String diagnosticMessage = databaseContextDiagnosticMessage(databaseSchemaContext);
        if (projectStructureMcp) {
            String projectMessage = "MCP 프로젝트 구조 분석 결과가 생성 결과에 반영되었습니다. 적용 Tool: analyze_project_structure.";
            return projectMessage + diagnosticMessage;
        }

        return diagnosticMessage.trim();
    }

    private String databaseContextDiagnosticMessage(DatabaseSchemaContext databaseSchemaContext) {
        if (!hasDatabaseContextDiagnostics(databaseSchemaContext)) {
            return "";
        }

        String attemptedText = databaseSchemaContext.attemptedTools().isEmpty()
                ? ""
                : " 시도 Tool: " + String.join(", ", databaseSchemaContext.attemptedTools()) + ".";
        String successText = databaseSchemaContext.appliedTools().isEmpty()
                ? ""
                : " 성공 Tool: " + String.join(", ", databaseSchemaContext.appliedTools()) + ".";
        String reasonText = databaseSchemaContext.hasStatusMessage()
                ? " 사유: " + databaseSchemaContext.statusMessage()
                : "";

        return " DB MCP Tool은 실행됐지만 대상 테이블을 확정하지 못해 생성 결과에는 반영되지 않았습니다."
                + attemptedText
                + successText
                + reasonText;
    }

    private boolean hasProjectStructureMcp(String analyzedProjectStructure) {
        return StringUtils.hasText(analyzedProjectStructure)
                && analyzedProjectStructure.contains("MCP project structure analysis");
    }

    private boolean hasAppliedDatabaseContext(DatabaseSchemaContext databaseSchemaContext) {
        return databaseSchemaContext != null && databaseSchemaContext.matchedTables();
    }

    private boolean hasDatabaseContextDiagnostics(DatabaseSchemaContext databaseSchemaContext) {
        return databaseSchemaContext != null
                && !databaseSchemaContext.matchedTables()
                && databaseSchemaContext.hasAttemptedTools();
    }

    private List<String> mcpAppliedTools(DatabaseSchemaContext databaseSchemaContext, String analyzedProjectStructure) {
        LinkedHashSet<String> tools = new LinkedHashSet<>();
        if (hasProjectStructureMcp(analyzedProjectStructure)) {
            tools.add("analyze_project_structure");
        }
        if (databaseSchemaContext != null && databaseSchemaContext.matchedTables()) {
            tools.addAll(databaseSchemaContext.appliedTools());
        }
        return List.copyOf(tools);
    }

    private String databaseSchemaContextText(DatabaseSchemaContext databaseSchemaContext) {
        if (databaseSchemaContext == null || !databaseSchemaContext.hasContent()) {
            return "MCP database schema context:\nNo matching DB table was found or MCP DB tools were unavailable. Use retrieved RAG source for table and field information.";
        }

        return "MCP database schema context:\n" + databaseSchemaContext.content();
    }

    private String outputFormatRule(List<String> targetTypes) {
        if (targetTypes.size() == 1) {
            return "Return only one Java source code output for the selected target type. Do not include multiple file path headers.";
        }
        return "Return one clearly separated Java source section for each selected target type, and do not include sections for unselected target types.";
    }

    private String abbreviate(String message) {
        if (!StringUtils.hasText(message)) {
            return "Unknown source graph indexing error.";
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
