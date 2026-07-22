package com.hanwha.ai.sourcegraph.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.hanwha.ai.generation.domain.GenerationHistory;
import com.hanwha.ai.sourcegraph.dto.JavaSourceGraphIngestRequest;
import com.hanwha.ai.sourcegraph.dto.SourceGraphNodeResponse;
import com.hanwha.ai.sourcegraph.dto.SourceGraphRelationshipResponse;
import com.hanwha.ai.sourcegraph.dto.SourceGraphResponse;
import com.hanwha.ai.sourcegraph.domain.SourceGraphIdentity;
import com.hanwha.ai.sourcegraph.domain.SourceOntology;
import com.hanwha.ai.sourcegraph.exception.NoJavaTypeFoundException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.UUID;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class JavaSourceGraphAnalyzer {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(?:java)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Set<String> COMMON_TYPES = Set.of(
            "String", "Boolean", "Byte", "Short", "Integer", "Long", "Float", "Double", "Character",
            "Object", "List", "Set", "Map", "Collection", "Optional", "Page", "Pageable", "ResponseEntity",
            "LocalDate", "LocalDateTime", "BigDecimal", "BigInteger", "Void"
    );
    private final StructuredSourceGraphAnalyzer structuredAnalyzer = new StructuredSourceGraphAnalyzer();

    public SourceGraphResponse analyzeSource(JavaSourceGraphIngestRequest request) {
        String fileName = request == null ? "" : firstText(request.fileName(), request.filePath());
        if (structuredAnalyzer.supports(fileName)) {
            return structuredAnalyzer.analyze(request);
        }
        return analyzeJavaSource(request);
    }

    public SourceGraphResponse analyze(GenerationHistory history) {
        if (history == null || history.getId() == null) {
            throw new IllegalArgumentException("Generation history id is required for source graph indexing.");
        }

        Long historyId = history.getId();
        String projectId = generationProjectId(history);
        String filePath = firstText(history.getTargetType(), "Generation" + historyId).replace('.', '/') + ".java";
        String indexedAt = Instant.now().toString();
        AnalysisTarget target = new AnalysisTarget(
                "generation:" + historyId,
                projectId,
                SourceGraphIdentity.DEFAULT_MODULE_NAME,
                SourceGraphIdentity.normalizeFilePath(filePath, filePath),
                null,
                List.of(),
                indexedAt,
                historyId,
                "Generation",
                generationUid(historyId),
                "Generation #" + historyId,
                "GENERATED",
                "generation-history",
                "generation-history:" + historyId,
                null,
                history.getTargetType(),
                history.getGeneratedCode(),
                properties(
                        "uid", generationUid(historyId),
                        "graphKey", "generation:" + historyId,
                        "historyId", historyId,
                        "id", historyId,
                        "projectId", projectId,
                        "targetType", history.getTargetType(),
                        "sourceKind", "generation-history",
                        "createdAt", history.getCreatedAt()
                )
        );
        return addStandardRuleEvidence(analyze(target), history, projectId);
    }

    public SourceGraphResponse analyzeJavaSource(JavaSourceGraphIngestRequest request) {
        if (request == null || !StringUtils.hasText(request.content())) {
            throw new IllegalArgumentException("Java source content is required.");
        }

        String source = firstText(request.source(), request.fileName(), "rag-inbox-java-source");
        String fileName = firstText(request.fileName(), source, "Source.java");
        String projectId = SourceGraphIdentity.projectId(request.projectId());
        String moduleName = SourceGraphIdentity.moduleName(request.moduleName());
        String filePath = SourceGraphIdentity.normalizeFilePath(request.filePath(), fileName);
        String graphKey = "rag-source:" + UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
        String rootUid = source.startsWith("document:")
                ? source
                : "document:" + UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
        String indexedAt = Instant.now().toString();

        AnalysisTarget target = new AnalysisTarget(
                graphKey,
                projectId,
                moduleName,
                filePath,
                request.fileHash(),
                request.chunkIds() == null ? List.of() : List.copyOf(request.chunkIds()),
                indexedAt,
                null,
                "Document",
                rootUid,
                fileName,
                "HAS_SOURCE",
                "rag-inbox-java",
                source,
                fileName,
                null,
                request.content(),
                properties(
                        "uid", rootUid,
                        "graphKey", graphKey,
                        "projectId", projectId,
                        "moduleName", moduleName,
                        "filePath", filePath,
                        "fileHash", request.fileHash(),
                        "sourceKind", "rag-inbox-java",
                        "source", source,
                        "fileName", fileName,
                        "name", fileName
                )
        );
        return addRuleEvaluations(analyze(target), request, target);
    }

    private SourceGraphResponse addRuleEvaluations(
            SourceGraphResponse graph,
            JavaSourceGraphIngestRequest request,
            AnalysisTarget target
    ) {
        List<String> conforms = request.conformsToRuleIds() == null
                ? List.of()
                : request.conformsToRuleIds();
        List<String> violations = request.violatedRuleIds() == null
                ? List.of()
                : request.violatedRuleIds();
        if (conforms.isEmpty() && violations.isEmpty()) {
            return graph;
        }

        Map<String, SourceGraphNodeResponse> nodes = new LinkedHashMap<>();
        graph.nodes().forEach(node -> nodes.put(node.id(), node));
        List<SourceGraphRelationshipResponse> relationships = new ArrayList<>(graph.relationships());
        List<SourceGraphNodeResponse> javaTypes = graph.nodes().stream()
                .filter(node -> "JavaType".equals(node.label()))
                .filter(node -> !Boolean.TRUE.equals(node.properties().get("external")))
                .toList();

        addRuleRelations(nodes, relationships, javaTypes, conforms, "CONFORMS_TO", target);
        addRuleRelations(nodes, relationships, javaTypes, violations, "VIOLATES", target);
        return new SourceGraphResponse(
                graph.historyId(),
                List.copyOf(nodes.values()),
                List.copyOf(relationships)
        );
    }

    private void addRuleRelations(
            Map<String, SourceGraphNodeResponse> nodes,
            List<SourceGraphRelationshipResponse> relationships,
            List<SourceGraphNodeResponse> javaTypes,
            List<String> ruleIds,
            String relationshipType,
            AnalysisTarget target
    ) {
        for (String ruleId : ruleIds) {
            if (!StringUtils.hasText(ruleId)) {
                continue;
            }
            String ruleUid = SourceGraphIdentity.standardRuleUid(target.projectId(), ruleId);
            nodes.putIfAbsent(ruleUid, new SourceGraphNodeResponse(
                    ruleUid,
                    "StandardRule",
                    ruleId,
                    mergeProperties(ontologyMetadata(target), properties(
                            "uid", ruleUid,
                            "name", ruleId,
                            "ruleId", ruleId
                    ))
            ));
            for (SourceGraphNodeResponse javaType : javaTypes) {
                relationships.add(new SourceGraphRelationshipResponse(
                        javaType.id(),
                        ruleUid,
                        relationshipType,
                        relationshipProperties(target)
                ));
            }
        }
    }

    private Map<String, Object> mergeProperties(
            Map<String, Object> base,
            Map<String, Object> additions
    ) {
        Map<String, Object> merged = new LinkedHashMap<>(base);
        merged.putAll(additions);
        return merged;
    }

    private SourceGraphResponse addStandardRuleEvidence(
            SourceGraphResponse graph,
            GenerationHistory history,
            String projectId
    ) {
        if (!StringUtils.hasText(history.getRagDocumentsJson())) {
            return graph;
        }
        List<String> documents;
        try {
            documents = OBJECT_MAPPER.readValue(
                    history.getRagDocumentsJson(),
                    new TypeReference<List<String>>() {
                    }
            );
        } catch (Exception exception) {
            return graph;
        }
        if (documents.isEmpty()) {
            return graph;
        }

        List<SourceGraphNodeResponse> nodes = new ArrayList<>(graph.nodes());
        List<SourceGraphRelationshipResponse> relationships = new ArrayList<>(graph.relationships());
        SourceGraphNodeResponse generation = graph.nodes().stream()
                .filter(node -> generationUid(history.getId()).equals(node.id()))
                .findFirst()
                .orElse(null);
        Map<String, Object> generationProperties = generation == null
                ? Map.of()
                : generation.properties();

        for (String document : documents) {
            if (!StringUtils.hasText(document)) {
                continue;
            }
            String ruleUid = "rule:" + projectId + ":"
                    + UUID.nameUUIDFromBytes(document.getBytes(StandardCharsets.UTF_8));
            Map<String, Object> ruleProperties = new LinkedHashMap<>(generationProperties);
            ruleProperties.put("uid", ruleUid);
            ruleProperties.put("name", abbreviateRuleName(document));
            ruleProperties.put("sourceKey", ruleSource(document));
            nodes.add(new SourceGraphNodeResponse(
                    ruleUid,
                    "StandardRule",
                    abbreviateRuleName(document),
                    ruleProperties
            ));

            Map<String, Object> relationshipProperties = new LinkedHashMap<>();
            for (String key : List.of(
                    "projectId", "sourceKey", "fileHash", "analysisVersion", "validFrom",
                    "validTo", "confidence", "extractor", "evidenceChunkIds", "createdAt", "graphKey"
            )) {
                if (generationProperties.containsKey(key)) {
                    relationshipProperties.put(key, generationProperties.get(key));
                }
            }
            relationships.add(new SourceGraphRelationshipResponse(
                    generationUid(history.getId()),
                    ruleUid,
                    "BASED_ON",
                    relationshipProperties
            ));
        }
        return new SourceGraphResponse(history.getId(), List.copyOf(nodes), List.copyOf(relationships));
    }

    private String abbreviateRuleName(String document) {
        String firstLine = document.strip().lines().findFirst().orElse("Standard rule");
        return firstLine.length() <= 160 ? firstLine : firstLine.substring(0, 160);
    }

    private String ruleSource(String document) {
        int marker = document.indexOf("[source:");
        int end = marker >= 0 ? document.indexOf(']', marker) : -1;
        return marker >= 0 && end > marker
                ? document.substring(marker + 8, end).trim()
                : "generation-rag";
    }

    public String findSourceContent(String content, String fqn, String simpleName) {
        if (!StringUtils.hasText(content) || (!StringUtils.hasText(fqn) && !StringUtils.hasText(simpleName))) {
            return "";
        }

        for (String candidate : extractJavaCandidates(content)) {
            CompilationUnit compilationUnit = parseCandidate(candidate);
            if (compilationUnit == null || compilationUnit.getTypes().isEmpty()) {
                continue;
            }

            String packageName = compilationUnit.getPackageDeclaration()
                    .map(packageDeclaration -> packageDeclaration.getName().asString())
                    .orElse("");
            for (TypeDeclaration<?> type : compilationUnit.getTypes()) {
                String candidateSimpleName = type.getNameAsString();
                String candidateFqn = fqn(packageName, candidateSimpleName);
                if (matchesType(candidateFqn, candidateSimpleName, fqn, simpleName)) {
                    return candidate;
                }
            }
        }
        return "";
    }

    private SourceGraphResponse analyze(AnalysisTarget target) {
        Map<String, SourceGraphNodeResponse> nodes = new LinkedHashMap<>();
        Set<RelationshipKey> relationships = new LinkedHashSet<>();

        addNode(nodes, target.rootUid(), target.rootLabel(), target.rootName(), target.rootProperties());
        for (String chunkId : target.evidenceChunkIds()) {
            addNode(nodes, chunkId, "Chunk", chunkId, properties(
                    "uid", chunkId,
                    "chunkId", chunkId,
                    "name", chunkId
            ));
            addRelationship(relationships, target.rootUid(), chunkId, "CONTAINS", target);
        }

        List<String> candidates = extractJavaCandidates(target.content());
        int parsedSources = 0;
        for (int index = 0; index < candidates.size(); index++) {
            CompilationUnit compilationUnit = parseCandidate(candidates.get(index));
            if (compilationUnit == null || compilationUnit.getTypes().isEmpty()) {
                continue;
            }

            parsedSources++;
            String packageName = compilationUnit.getPackageDeclaration()
                    .map(packageDeclaration -> packageDeclaration.getName().asString())
                    .orElse("");
            Map<String, String> importMap = importMap(compilationUnit);
            String projectUid = SourceGraphIdentity.projectUid(target.projectId());
            String moduleUid = SourceGraphIdentity.moduleUid(target.projectId(), target.moduleName());
            String packageUid = SourceGraphIdentity.packageUid(target.projectId(), packageName);
            String sourceUid = SourceGraphIdentity.sourceFileUid(target.projectId(), target.filePath());

            addNode(nodes, projectUid, "Project", target.projectId(), properties(
                    "uid", projectUid,
                    "name", target.projectId()
            ));
            addNode(nodes, moduleUid, "Module", target.moduleName(), properties(
                    "uid", moduleUid,
                    "name", target.moduleName()
            ));
            addNode(nodes, packageUid, "Package", packageName, properties(
                    "uid", packageUid,
                    "name", StringUtils.hasText(packageName) ? packageName : "(default)"
            ));
            addNode(nodes, sourceUid, "SourceFile", target.fileName(), properties(
                    "uid", sourceUid,
                    "graphKey", target.graphKey(),
                    "filePath", target.filePath(),
                    "fileHash", target.fileHash(),
                    "packageName", packageName,
                    "fileName", target.fileName(),
                    "name", target.fileName()
            ));
            addRelationship(relationships, projectUid, moduleUid, "CONTAINS", target);
            addRelationship(relationships, moduleUid, packageUid, "CONTAINS", target);
            addRelationship(relationships, packageUid, sourceUid, "CONTAINS", target);
            addRelationship(relationships, target.rootUid(), sourceUid, target.rootToSourceRelationship(), target);

            for (TypeDeclaration<?> type : compilationUnit.getTypes()) {
                String simpleName = type.getNameAsString();
                String fqn = fqn(packageName, simpleName);
                String sourceFileName = firstText(target.fileName(), simpleName + ".java");
                String typeUid = SourceGraphIdentity.typeUid(target.projectId(), fqn);
                String typeKind = typeKind(type);
                String layer = inferLayer(simpleName, type);
                addNode(nodes, sourceUid, "SourceFile", sourceFileName, properties(
                        "uid", sourceUid,
                        "graphKey", target.graphKey(),
                        "historyId", target.historyId(),
                        "projectId", target.projectId(),
                        "moduleName", target.moduleName(),
                        "filePath", target.filePath(),
                        "fileHash", target.fileHash(),
                        "sourceKind", target.sourceKind(),
                        "source", target.source(),
                        "fileName", sourceFileName,
                        "packageName", packageName,
                        "primaryType", fqn
                ));
                addJavaTypeNode(nodes, target, typeUid, fqn, simpleName, typeKind, layer, false);
                addNode(nodes, typeUid, "JavaType", simpleName, properties(
                        "annotations", annotationNames(type),
                        "springStereotype", springStereotype(layer)
                ));
                addRelationship(relationships, sourceUid, typeUid, "DECLARES", target);
                for (String chunkId : target.evidenceChunkIds()) {
                    addRelationship(relationships, chunkId, typeUid, "DESCRIBES", target);
                    addRelationship(relationships, chunkId, typeUid, "EVIDENCE_FOR", target);
                }

                addImports(nodes, relationships, target, typeUid, compilationUnit);
                addInheritance(nodes, relationships, target, typeUid, packageName, importMap, type);
                addFields(nodes, relationships, target, typeUid, packageName, importMap, type);
                addConstructors(nodes, relationships, target, typeUid, packageName, importMap, type);
                addMethods(nodes, relationships, target, typeUid, packageName, importMap, type);
                evaluateControllerRepositoryRule(nodes, relationships, target, typeUid, layer);
                evaluateServiceImplementationRule(nodes, relationships, target, typeUid, type, layer);
            }
        }

        if (parsedSources == 0) {
            throw new NoJavaTypeFoundException("Java source did not contain parsable Java code.");
        }

        enrichNodes(nodes, target);

        return new SourceGraphResponse(
                target.historyId(),
                List.copyOf(nodes.values()),
                relationships.stream()
                        .map(key -> new SourceGraphRelationshipResponse(
                                key.sourceId(),
                                key.targetId(),
                                key.type(),
                                relationshipProperties(target)
                        ))
                        .toList()
        );
    }

    private void addImports(
            Map<String, SourceGraphNodeResponse> nodes,
            Set<RelationshipKey> relationships,
            AnalysisTarget target,
            String sourceTypeUid,
            CompilationUnit compilationUnit
    ) {
        for (ImportDeclaration importDeclaration : compilationUnit.getImports()) {
            if (importDeclaration.isAsterisk() || importDeclaration.isStatic()) {
                continue;
            }
            String importFqn = importDeclaration.getNameAsString();
            if (shouldSkipFqn(importFqn)) {
                continue;
            }
            String targetUid = SourceGraphIdentity.typeUid(target.projectId(), importFqn);
            addJavaTypeNode(nodes, target, targetUid, importFqn, simpleName(importFqn), "UNKNOWN", inferLayer(simpleName(importFqn), null), true);
            addRelationship(relationships, sourceTypeUid, targetUid, "IMPORTS", target);
        }
    }

    private void addInheritance(
            Map<String, SourceGraphNodeResponse> nodes,
            Set<RelationshipKey> relationships,
            AnalysisTarget target,
            String sourceTypeUid,
            String packageName,
            Map<String, String> importMap,
            TypeDeclaration<?> type
    ) {
        if (!type.isClassOrInterfaceDeclaration()) {
            return;
        }

        type.asClassOrInterfaceDeclaration().getExtendedTypes().forEach(extendedType -> addTypeRelationship(
                nodes, relationships, target, sourceTypeUid, packageName, importMap, extendedType, "EXTENDS"
        ));
        type.asClassOrInterfaceDeclaration().getImplementedTypes().forEach(implementedType -> addTypeRelationship(
                nodes, relationships, target, sourceTypeUid, packageName, importMap, implementedType, "IMPLEMENTS"
        ));
    }

    private void addFields(
            Map<String, SourceGraphNodeResponse> nodes,
            Set<RelationshipKey> relationships,
            AnalysisTarget target,
            String sourceTypeUid,
            String packageName,
            Map<String, String> importMap,
            TypeDeclaration<?> type
    ) {
        String declaringFqn = stringProperty(nodes.get(sourceTypeUid), "fqn");
        for (FieldDeclaration field : type.findAll(FieldDeclaration.class)) {
            field.getVariables().forEach(variable -> {
                String fieldUid = SourceGraphIdentity.fieldUid(
                        target.projectId(),
                        declaringFqn,
                        variable.getNameAsString()
                );
                addNode(nodes, fieldUid, "Field", variable.getNameAsString(), properties(
                        "uid", fieldUid,
                        "name", variable.getNameAsString(),
                        "fieldType", variable.getType().asString().replaceAll("\\s+", ""),
                        "declaringType", declaringFqn
                ));
                addRelationship(relationships, sourceTypeUid, fieldUid, "HAS_FIELD", target);
            });
            addTypeRelationship(nodes, relationships, target, sourceTypeUid, packageName, importMap, field.getElementType(), "INJECTS");
        }
    }

    private void addConstructors(
            Map<String, SourceGraphNodeResponse> nodes,
            Set<RelationshipKey> relationships,
            AnalysisTarget target,
            String sourceTypeUid,
            String packageName,
            Map<String, String> importMap,
            TypeDeclaration<?> type
    ) {
        for (ConstructorDeclaration constructor : type.findAll(ConstructorDeclaration.class)) {
            constructor.getParameters().forEach(parameter -> addTypeRelationship(
                    nodes, relationships, target, sourceTypeUid, packageName, importMap, parameter.getType(), "INJECTS"
            ));
        }
    }

    private void addMethods(
            Map<String, SourceGraphNodeResponse> nodes,
            Set<RelationshipKey> relationships,
            AnalysisTarget target,
            String sourceTypeUid,
            String packageName,
            Map<String, String> importMap,
            TypeDeclaration<?> type
    ) {
        Map<String, String> fieldTypes = fieldTypeMap(type, packageName, importMap);
        String sourceLayer = stringProperty(nodes.get(sourceTypeUid), "layer");
        for (MethodDeclaration method : type.getMethods()) {
            String signature = methodSignature(method);
            String declaringFqn = stringProperty(nodes.get(sourceTypeUid), "fqn");
            String methodUid = SourceGraphIdentity.methodUid(target.projectId(), declaringFqn, signature);
            addNode(nodes, methodUid, "Method", method.getNameAsString(), properties(
                    "uid", methodUid,
                    "graphKey", target.graphKey(),
                    "historyId", target.historyId(),
                    "projectId", target.projectId(),
                    "moduleName", target.moduleName(),
                    "filePath", target.filePath(),
                    "fileHash", target.fileHash(),
                    "sourceKind", target.sourceKind(),
                    "source", target.source(),
                    "name", method.getNameAsString(),
                    "signature", signature,
                    "declaringType", declaringFqn,
                    "annotations", annotationNames(method),
                    "transactional", hasAnnotation(method, "Transactional")
            ));
            addRelationship(relationships, sourceTypeUid, methodUid, "HAS_METHOD", target);
            addApiEndpoint(nodes, relationships, target, type, method, methodUid);
            addSameTypeMethodCalls(nodes, relationships, target, type, method, declaringFqn, methodUid);
            addScopedMethodCalls(nodes, relationships, target, method, methodUid, packageName,
                    importMap, fieldTypes, sourceTypeUid, sourceLayer);
            addSqlOntology(nodes, relationships, target, sourceTypeUid, method, methodUid, declaringFqn, signature);
            addTypeRelationship(nodes, relationships, target, sourceTypeUid, packageName, importMap, method.getType(), "USES");
            method.getParameters().forEach(parameter -> addTypeRelationship(
                    nodes, relationships, target, sourceTypeUid, packageName, importMap, parameter.getType(), "USES"
            ));
            method.getThrownExceptions().forEach(thrownType -> addTypeRelationship(
                    nodes, relationships, target, sourceTypeUid, packageName, importMap, thrownType, "USES"
            ));
            addDtoDomainMappings(nodes, relationships, target, method, packageName, importMap);
        }
    }

    private List<String> annotationNames(NodeWithAnnotations<?> node) {
        return node.getAnnotations().stream()
                .map(annotation -> annotation.getNameAsString())
                .distinct()
                .toList();
    }

    private boolean hasAnnotation(NodeWithAnnotations<?> node, String annotationName) {
        return node.getAnnotations().stream()
                .anyMatch(annotation -> annotationName.equals(annotation.getNameAsString()));
    }

    private String springStereotype(String layer) {
        return Set.of("Controller", "Service", "Repository").contains(layer) ? layer : "";
    }

    private Map<String, String> fieldTypeMap(
            TypeDeclaration<?> type,
            String packageName,
            Map<String, String> importMap
    ) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldDeclaration field : type.getFields()) {
            String typeName = field.getElementType().asString();
            int genericIndex = typeName.indexOf('<');
            if (genericIndex >= 0) {
                typeName = typeName.substring(0, genericIndex);
            }
            String resolvedType = resolveFqn(typeName, packageName, importMap);
            for (var variable : field.getVariables()) {
                fields.put(variable.getNameAsString(), resolvedType);
            }
        }
        return fields;
    }

    private void addApiEndpoint(
            Map<String, SourceGraphNodeResponse> nodes,
            Set<RelationshipKey> relationships,
            AnalysisTarget target,
            TypeDeclaration<?> type,
            MethodDeclaration method,
            String methodUid
    ) {
        EndpointMapping mapping = endpointMapping(type, method);
        if (mapping == null) {
            return;
        }
        String endpointUid = SourceGraphIdentity.endpointUid(
                target.projectId(),
                mapping.httpMethod(),
                mapping.path()
        );
        addNode(nodes, endpointUid, "ApiEndpoint", mapping.httpMethod() + " " + mapping.path(), properties(
                "uid", endpointUid,
                "name", mapping.httpMethod() + " " + mapping.path(),
                "httpMethod", mapping.httpMethod(),
                "path", mapping.path()
        ));
        addRelationship(relationships, endpointUid, methodUid, "HANDLED_BY", target);
    }

    private EndpointMapping endpointMapping(TypeDeclaration<?> type, MethodDeclaration method) {
        Map<String, String> httpMethods = Map.of(
                "GetMapping", "GET",
                "PostMapping", "POST",
                "PutMapping", "PUT",
                "PatchMapping", "PATCH",
                "DeleteMapping", "DELETE",
                "RequestMapping", "REQUEST"
        );
        AnnotationExpr mappingAnnotation = method.getAnnotations().stream()
                .filter(annotation -> httpMethods.containsKey(annotation.getNameAsString()))
                .findFirst()
                .orElse(null);
        if (mappingAnnotation == null) {
            return null;
        }
        String classPath = type.getAnnotations().stream()
                .filter(annotation -> "RequestMapping".equals(annotation.getNameAsString()))
                .findFirst()
                .map(this::annotationPath)
                .orElse("");
        String methodPath = annotationPath(mappingAnnotation);
        String httpMethod = httpMethods.get(mappingAnnotation.getNameAsString());
        if ("REQUEST".equals(httpMethod)) {
            Matcher methodMatcher = Pattern.compile("RequestMethod\\.([A-Z]+)").matcher(mappingAnnotation.toString());
            httpMethod = methodMatcher.find() ? methodMatcher.group(1) : "REQUEST";
        }
        return new EndpointMapping(httpMethod, normalizeEndpointPath(classPath, methodPath));
    }

    private String annotationPath(AnnotationExpr annotation) {
        Matcher matcher = Pattern.compile("\"([^\"]*)\"").matcher(annotation.toString());
        return matcher.find() ? matcher.group(1) : "";
    }

    private String normalizeEndpointPath(String classPath, String methodPath) {
        String combined = ("/" + firstText(classPath, "") + "/" + firstText(methodPath, ""))
                .replaceAll("/+", "/");
        return combined.length() > 1 && combined.endsWith("/")
                ? combined.substring(0, combined.length() - 1)
                : combined;
    }

    private void addSameTypeMethodCalls(
            Map<String, SourceGraphNodeResponse> nodes,
            Set<RelationshipKey> relationships,
            AnalysisTarget target,
            TypeDeclaration<?> type,
            MethodDeclaration sourceMethod,
            String declaringFqn,
            String sourceMethodUid
    ) {
        for (MethodCallExpr call : sourceMethod.findAll(MethodCallExpr.class)) {
            boolean localCall = call.getScope().isEmpty()
                    || "this".equals(call.getScope().map(Object::toString).orElse(""));
            if (!localCall) {
                continue;
            }
            MethodDeclaration targetMethod = type.getMethods().stream()
                    .filter(candidate -> candidate.getNameAsString().equals(call.getNameAsString()))
                    .filter(candidate -> candidate.getParameters().size() == call.getArguments().size())
                    .findFirst()
                    .orElse(null);
            if (targetMethod == null) {
                continue;
            }
            String targetSignature = methodSignature(targetMethod);
            String targetMethodUid = SourceGraphIdentity.methodUid(
                    target.projectId(),
                    declaringFqn,
                    targetSignature
            );
            if (sourceMethodUid.equals(targetMethodUid)) {
                continue;
            }
            addNode(nodes, targetMethodUid, "Method", targetMethod.getNameAsString(), properties(
                    "uid", targetMethodUid,
                    "name", targetMethod.getNameAsString(),
                    "signature", targetSignature,
                    "declaringType", declaringFqn
            ));
            addRelationship(relationships, sourceMethodUid, targetMethodUid, "CALLS", target);
        }
    }

    private void addScopedMethodCalls(
            Map<String, SourceGraphNodeResponse> nodes,
            Set<RelationshipKey> relationships,
            AnalysisTarget target,
            MethodDeclaration sourceMethod,
            String sourceMethodUid,
            String packageName,
            Map<String, String> importMap,
            Map<String, String> fieldTypes,
            String sourceTypeUid,
            String sourceLayer
    ) {
        Map<String, String> parameterTypes = new LinkedHashMap<>();
        sourceMethod.getParameters().forEach(parameter -> parameterTypes.put(
                parameter.getNameAsString(), parameter.getType().asString().replaceAll("\\s+", "")
        ));
        for (MethodCallExpr call : sourceMethod.findAll(MethodCallExpr.class)) {
            String scope = call.getScope().map(Object::toString).orElse("");
            if (scope.startsWith("this.")) {
                scope = scope.substring(5);
            }
            String targetFqn = fieldTypes.get(scope);
            if (!StringUtils.hasText(targetFqn)) {
                continue;
            }
            String targetLayer = inferLayer(simpleName(targetFqn), null);
            String targetTypeUid = SourceGraphIdentity.typeUid(target.projectId(), targetFqn);
            addJavaTypeNode(nodes, target, targetTypeUid, targetFqn, simpleName(targetFqn),
                    "UNKNOWN", targetLayer, true);
            String signature = inferredCallSignature(call, parameterTypes, packageName, importMap);
            String targetMethodUid = SourceGraphIdentity.methodUid(target.projectId(), targetFqn, signature);
            addNode(nodes, targetMethodUid, "Method", call.getNameAsString(), properties(
                    "uid", targetMethodUid,
                    "name", call.getNameAsString(),
                    "signature", signature,
                    "declaringType", targetFqn,
                    "external", true,
                    "resolvedFrom", "injected-field-call"
            ));
            addRelationship(relationships, targetTypeUid, targetMethodUid, "HAS_METHOD", target);
            addRelationship(relationships, sourceMethodUid, targetMethodUid, "CALLS", target);
            if ("Controller".equals(sourceLayer) && "Repository".equals(targetLayer)) {
                addArchitectureRule(nodes, relationships, target, sourceTypeUid,
                        "controller-must-not-call-repository", true);
            }
        }
    }

    private String inferredCallSignature(
            MethodCallExpr call,
            Map<String, String> parameterTypes,
            String packageName,
            Map<String, String> importMap
    ) {
        List<String> argumentTypes = call.getArguments().stream().map(argument -> {
            if (argument.isNameExpr()) {
                return parameterTypes.getOrDefault(argument.asNameExpr().getNameAsString(), "?");
            }
            if (argument.isStringLiteralExpr()) {
                return "String";
            }
            if (argument.isIntegerLiteralExpr()) {
                return "int";
            }
            if (argument.isLongLiteralExpr()) {
                return "long";
            }
            if (argument.isBooleanLiteralExpr()) {
                return "boolean";
            }
            if (argument.isObjectCreationExpr()) {
                return simpleName(resolveFqn(argument.asObjectCreationExpr().getType().getNameAsString(),
                        packageName, importMap));
            }
            return "?";
        }).toList();
        return call.getNameAsString() + "(" + String.join(",", argumentTypes) + ")";
    }

    private void addDtoDomainMappings(
            Map<String, SourceGraphNodeResponse> nodes,
            Set<RelationshipKey> relationships,
            AnalysisTarget target,
            MethodDeclaration method,
            String packageName,
            Map<String, String> importMap
    ) {
        Set<String> dtoUids = new LinkedHashSet<>();
        Set<String> domainUids = new LinkedHashSet<>();
        List<Type> types = new ArrayList<>();
        types.add(method.getType());
        method.getParameters().forEach(parameter -> types.add(parameter.getType()));
        for (Type type : types) {
            for (ClassOrInterfaceType classType : classTypes(type)) {
                String typeFqn = resolveFqn(classType.getNameAsString(), packageName, importMap);
                String layer = inferLayer(simpleName(typeFqn), null);
                if (!"DTO".equals(layer) && !"Domain".equals(layer)) {
                    continue;
                }
                String typeUid = SourceGraphIdentity.typeUid(target.projectId(), typeFqn);
                addJavaTypeNode(nodes, target, typeUid, typeFqn, simpleName(typeFqn), "UNKNOWN", layer, true);
                ("DTO".equals(layer) ? dtoUids : domainUids).add(typeUid);
            }
        }
        for (String dtoUid : dtoUids) {
            for (String domainUid : domainUids) {
                addRelationship(relationships, dtoUid, domainUid, "MAPS_TO", target);
            }
        }
    }

    private void evaluateControllerRepositoryRule(
            Map<String, SourceGraphNodeResponse> nodes,
            Set<RelationshipKey> relationships,
            AnalysisTarget target,
            String typeUid,
            String layer
    ) {
        if (!"Controller".equals(layer)) {
            return;
        }
        String ruleUid = SourceGraphIdentity.standardRuleUid(
                target.projectId(), "controller-must-not-call-repository"
        );
        boolean violates = relationships.contains(new RelationshipKey(typeUid, ruleUid, "VIOLATES"));
        addArchitectureRule(nodes, relationships, target, typeUid,
                "controller-must-not-call-repository", violates);
    }

    private void evaluateServiceImplementationRule(
            Map<String, SourceGraphNodeResponse> nodes,
            Set<RelationshipKey> relationships,
            AnalysisTarget target,
            String typeUid,
            TypeDeclaration<?> type,
            String layer
    ) {
        if (!"ServiceImpl".equals(layer) || !type.isClassOrInterfaceDeclaration()) {
            return;
        }
        boolean implementsService = type.asClassOrInterfaceDeclaration().getImplementedTypes().stream()
                .map(implementedType -> implementedType.getNameAsString().toLowerCase(Locale.ROOT))
                .anyMatch(name -> name.endsWith("service"));
        addArchitectureRule(nodes, relationships, target, typeUid,
                "service-impl-must-implement-service", !implementsService);
    }

    private void addArchitectureRule(
            Map<String, SourceGraphNodeResponse> nodes,
            Set<RelationshipKey> relationships,
            AnalysisTarget target,
            String typeUid,
            String ruleId,
            boolean violates
    ) {
        String ruleUid = SourceGraphIdentity.standardRuleUid(target.projectId(), ruleId);
        String statement = "controller-must-not-call-repository".equals(ruleId)
                ? "Controller must not call Repository directly."
                : "ServiceImpl must implement a Service interface.";
        addNode(nodes, ruleUid, "StandardRule", ruleId, properties(
                "uid", ruleUid,
                "name", ruleId,
                "ruleId", ruleId,
                "ruleType", "ARCHITECTURE",
                "severity", "ERROR",
                "statement", statement
        ));
        String relation = violates ? "VIOLATES" : "CONFORMS_TO";
        relationships.remove(new RelationshipKey(typeUid, ruleUid,
                violates ? "CONFORMS_TO" : "VIOLATES"));
        addRelationship(relationships, typeUid, ruleUid, relation, target);
    }

    private void addSqlOntology(
            Map<String, SourceGraphNodeResponse> nodes,
            Set<RelationshipKey> relationships,
            AnalysisTarget target,
            String sourceTypeUid,
            MethodDeclaration method,
            String methodUid,
            String declaringFqn,
            String signature
    ) {
        Map<String, String> operations = Map.of(
                "Select", "READ",
                "Insert", "WRITE",
                "Update", "WRITE",
                "Delete", "WRITE"
        );
        AnnotationExpr sqlAnnotation = method.getAnnotations().stream()
                .filter(annotation -> operations.containsKey(annotation.getNameAsString()))
                .findFirst()
                .orElse(null);
        if (sqlAnnotation == null) {
            return;
        }
        String sql = annotationStrings(sqlAnnotation);
        if (!StringUtils.hasText(sql)) {
            return;
        }
        String operation = operations.get(sqlAnnotation.getNameAsString());
        String statementUid = SourceGraphIdentity.sqlStatementUid(
                target.projectId(), declaringFqn, signature
        );
        addNode(nodes, statementUid, "SqlStatement", method.getNameAsString(), properties(
                "uid", statementUid,
                "name", method.getNameAsString(),
                "operation", operation,
                "sql", sql,
                "declaringType", declaringFqn
        ));
        addRelationship(relationships, methodUid, statementUid, "EXECUTES", target);

        for (TableReference table : sqlTables(sql, operation)) {
            String tableUid = SourceGraphIdentity.tableUid(
                    target.projectId(), table.schema(), table.tableName()
            );
            addNode(nodes, tableUid, "DatabaseTable", table.qualifiedName(), properties(
                    "uid", tableUid,
                    "name", table.tableName(),
                    "datasourceId", target.projectId(),
                    "schema", table.schema(),
                    "tableName", table.tableName()
            ));
            addRelationship(
                    relationships,
                    methodUid,
                    tableUid,
                    "READ".equals(operation) ? "READS_FROM" : "WRITES_TO",
                    target
            );
            addRelationship(
                    relationships,
                    statementUid,
                    tableUid,
                    "READ".equals(operation) ? "READS_FROM" : "WRITES_TO",
                    target
            );
            if ("Mapper".equals(stringProperty(nodes.get(sourceTypeUid), "layer"))) {
                addRelationship(relationships, sourceTypeUid, tableUid, "MAPS_TO", target);
            }
            for (String columnName : sqlColumns(sql, operation)) {
                String columnUid = SourceGraphIdentity.columnUid(
                        target.projectId(), table.schema(), table.tableName(), columnName
                );
                addNode(nodes, columnUid, "DatabaseColumn", columnName, properties(
                        "uid", columnUid,
                        "name", columnName,
                        "datasourceId", target.projectId(),
                        "schema", table.schema(),
                        "tableName", table.tableName(),
                        "columnName", columnName
                ));
                addRelationship(relationships, tableUid, columnUid, "HAS_COLUMN", target);
                addRelationship(relationships, statementUid, columnUid, "REFERENCES_COLUMN", target);
            }
        }
    }

    private String annotationStrings(AnnotationExpr annotation) {
        Matcher matcher = Pattern.compile("\"([^\"]*)\"").matcher(annotation.toString());
        List<String> values = new ArrayList<>();
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return String.join(" ", values);
    }

    private List<TableReference> sqlTables(String sql, String operation) {
        String normalized = sql.replace(',', ' ').replace('(', ' ').replace(')', ' ');
        StringTokenizer tokenizer = new StringTokenizer(normalized);
        List<String> tokens = new ArrayList<>();
        while (tokenizer.hasMoreTokens()) {
            tokens.add(tokenizer.nextToken());
        }
        Map<String, TableReference> tables = new LinkedHashMap<>();
        for (int index = 0; index < tokens.size() - 1; index++) {
            String token = tokens.get(index).toUpperCase(Locale.ROOT);
            boolean tableFollows = "READ".equals(operation)
                    ? "FROM".equals(token) || "JOIN".equals(token)
                    : "INTO".equals(token) || "UPDATE".equals(token)
                    || ("FROM".equals(token) && index > 0
                    && "DELETE".equals(tokens.get(index - 1).toUpperCase(Locale.ROOT)));
            if (!tableFollows) {
                continue;
            }
            String qualifiedName = cleanSqlIdentifier(tokens.get(index + 1));
            int separator = qualifiedName.lastIndexOf('.');
            String schema = separator >= 0 ? qualifiedName.substring(0, separator) : "public";
            String tableName = separator >= 0 ? qualifiedName.substring(separator + 1) : qualifiedName;
            if (StringUtils.hasText(tableName)) {
                tables.putIfAbsent(qualifiedName, new TableReference(schema, tableName));
            }
        }
        return List.copyOf(tables.values());
    }

    private String cleanSqlIdentifier(String value) {
        StringBuilder cleaned = new StringBuilder();
        for (char character : value.toCharArray()) {
            if (Character.isLetterOrDigit(character) || character == '_' || character == '.') {
                cleaned.append(character);
            }
        }
        return cleaned.toString();
    }

    private List<String> sqlColumns(String sql, String operation) {
        String upper = sql.toUpperCase(Locale.ROOT);
        String expression = "";
        if ("READ".equals(operation)) {
            int start = upper.indexOf("SELECT");
            int end = upper.indexOf("FROM", start + 6);
            if (start >= 0 && end > start) {
                expression = sql.substring(start + 6, end);
            }
        } else if (upper.startsWith("INSERT")) {
            int start = sql.indexOf('(');
            int end = sql.indexOf(')', start + 1);
            if (start >= 0 && end > start) {
                expression = sql.substring(start + 1, end);
            }
        } else if (upper.startsWith("UPDATE")) {
            int start = upper.indexOf(" SET ");
            int end = upper.indexOf(" WHERE ", start + 5);
            if (start >= 0) {
                expression = sql.substring(start + 5, end > start ? end : sql.length());
            }
        }
        if (!StringUtils.hasText(expression) || expression.contains("*")) {
            return List.of();
        }

        Set<String> columns = new LinkedHashSet<>();
        for (String candidate : expression.split(",")) {
            String value = candidate.trim();
            String upperValue = value.toUpperCase(Locale.ROOT);
            int alias = upperValue.indexOf(" AS ");
            if (alias >= 0) {
                value = value.substring(0, alias);
            }
            int assignment = value.indexOf('=');
            if (assignment >= 0) {
                value = value.substring(0, assignment);
            }
            StringTokenizer tokenizer = new StringTokenizer(value);
            if (tokenizer.hasMoreTokens()) {
                value = tokenizer.nextToken();
            }
            String cleaned = cleanSqlIdentifier(value);
            int separator = cleaned.lastIndexOf('.');
            String columnName = separator >= 0 ? cleaned.substring(separator + 1) : cleaned;
            if (StringUtils.hasText(columnName)) {
                columns.add(columnName);
            }
        }
        return List.copyOf(columns);
    }

    private void addTypeRelationship(
            Map<String, SourceGraphNodeResponse> nodes,
            Set<RelationshipKey> relationships,
            AnalysisTarget target,
            String sourceTypeUid,
            String packageName,
            Map<String, String> importMap,
            Type type,
            String relationshipType
    ) {
        for (ClassOrInterfaceType classType : classTypes(type)) {
            String simpleName = classType.getNameAsString();
            if (COMMON_TYPES.contains(simpleName)) {
                continue;
            }
            String targetFqn = resolveFqn(simpleName, packageName, importMap);
            if (shouldSkipFqn(targetFqn)) {
                continue;
            }
            String targetUid = SourceGraphIdentity.typeUid(target.projectId(), targetFqn);
            String targetLayer = inferLayer(simpleName(targetFqn), null);
            addJavaTypeNode(nodes, target, targetUid, targetFqn, simpleName(targetFqn), "UNKNOWN", targetLayer, true);
            if (!targetUid.equals(sourceTypeUid)) {
                addRelationship(relationships, sourceTypeUid, targetUid, relationshipType, target);
                if ("USES".equals(relationshipType) && "DTO".equals(targetLayer)) {
                    addRelationship(relationships, sourceTypeUid, targetUid, "USES_DTO", target);
                }
            }
        }
    }

    private List<ClassOrInterfaceType> classTypes(Type type) {
        List<ClassOrInterfaceType> classTypes = new ArrayList<>();
        if (type.isClassOrInterfaceType()) {
            classTypes.add(type.asClassOrInterfaceType());
        }
        classTypes.addAll(type.findAll(ClassOrInterfaceType.class));
        return classTypes.stream()
                .collect(LinkedHashMap<String, ClassOrInterfaceType>::new,
                        (map, classType) -> map.putIfAbsent(classType.getNameAsString(), classType),
                        LinkedHashMap::putAll)
                .values()
                .stream()
                .toList();
    }

    private List<String> extractJavaCandidates(String generatedCode) {
        if (!StringUtils.hasText(generatedCode)) {
            return List.of();
        }

        List<String> candidates = new ArrayList<>();
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(generatedCode);
        while (matcher.find()) {
            String candidate = trimToJava(matcher.group(1));
            if (StringUtils.hasText(candidate)) {
                candidates.add(candidate);
            }
        }
        if (candidates.isEmpty()) {
            candidates.add(trimToJava(generatedCode));
        }
        return candidates;
    }

    private CompilationUnit parseCandidate(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return null;
        }

        try {
            return StaticJavaParser.parse(candidate);
        } catch (ParseProblemException firstFailure) {
            String trimmed = trimToJava(candidate);
            if (trimmed.equals(candidate)) {
                return null;
            }
            try {
                return StaticJavaParser.parse(trimmed);
            } catch (ParseProblemException secondFailure) {
                return null;
            }
        }
    }

    private String trimToJava(String value) {
        String normalized = value == null ? "" : value.strip();
        List<Integer> indexes = List.of(
                        normalized.indexOf("package "),
                        normalized.indexOf("import "),
                        normalized.indexOf("@"),
                        normalized.indexOf("public "),
                        normalized.indexOf("class "),
                        normalized.indexOf("interface "),
                        normalized.indexOf("enum "),
                        normalized.indexOf("record ")
                ).stream()
                .filter(index -> index >= 0)
                .sorted(Comparator.naturalOrder())
                .toList();
        if (indexes.isEmpty()) {
            return normalized;
        }
        return normalized.substring(indexes.get(0)).strip();
    }

    private Map<String, String> importMap(CompilationUnit compilationUnit) {
        Map<String, String> imports = new LinkedHashMap<>();
        for (ImportDeclaration importDeclaration : compilationUnit.getImports()) {
            if (importDeclaration.isAsterisk() || importDeclaration.isStatic()) {
                continue;
            }
            String fqn = importDeclaration.getNameAsString();
            imports.put(simpleName(fqn), fqn);
        }
        return imports;
    }

    private String resolveFqn(String simpleName, String packageName, Map<String, String> importMap) {
        if (simpleName.contains(".")) {
            return simpleName;
        }
        String imported = importMap.get(simpleName);
        if (imported != null) {
            return imported;
        }
        if (StringUtils.hasText(packageName)) {
            return packageName + "." + simpleName;
        }
        return simpleName;
    }

    private boolean matchesType(String candidateFqn, String candidateSimpleName, String requestedFqn, String requestedSimpleName) {
        if (StringUtils.hasText(requestedFqn) && requestedFqn.equals(candidateFqn)) {
            return true;
        }
        if (StringUtils.hasText(requestedSimpleName) && requestedSimpleName.equals(candidateSimpleName)) {
            return true;
        }
        if (StringUtils.hasText(requestedFqn)) {
            return simpleName(requestedFqn).equals(candidateSimpleName);
        }
        return false;
    }
    private void addJavaTypeNode(
            Map<String, SourceGraphNodeResponse> nodes,
            AnalysisTarget target,
            String uid,
            String fqn,
            String simpleName,
            String kind,
            String layer,
            boolean external
    ) {
        addNode(nodes, uid, "JavaType", simpleName, properties(
                "uid", uid,
                "graphKey", target.graphKey(),
                "historyId", target.historyId(),
                "projectId", target.projectId(),
                "moduleName", target.moduleName(),
                "filePath", target.filePath(),
                "fileHash", target.fileHash(),
                "sourceKind", target.sourceKind(),
                "source", target.source(),
                "fqn", fqn,
                "simpleName", simpleName,
                "kind", kind,
                "layer", layer,
                "external", external
        ));
    }

    private void addNode(Map<String, SourceGraphNodeResponse> nodes, String id, String label, String name, Map<String, Object> properties) {
        nodes.merge(id, new SourceGraphNodeResponse(id, label, name, properties), (current, next) -> {
            Map<String, Object> merged = new LinkedHashMap<>(current.properties());
            merged.putAll(next.properties());
            if (Boolean.FALSE.equals(next.properties().get("external"))) {
                merged.put("external", false);
            }
            return new SourceGraphNodeResponse(current.id(), current.label(), next.name(), merged);
        });
    }

    private void addRelationship(Set<RelationshipKey> relationships, String sourceId, String targetId, String type, AnalysisTarget target) {
        if (sourceId == null || targetId == null || type == null || target == null) {
            return;
        }
        relationships.add(new RelationshipKey(sourceId, targetId, type));
    }

    private Map<String, Object> properties(Object... keyValues) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length - 1; index += 2) {
            Object value = keyValues[index + 1];
            if (value != null) {
                properties.put(String.valueOf(keyValues[index]), value);
            }
        }
        return properties;
    }

    private void enrichNodes(Map<String, SourceGraphNodeResponse> nodes, AnalysisTarget target) {
        nodes.replaceAll((uid, node) -> {
            Map<String, Object> enriched = new LinkedHashMap<>(ontologyMetadata(target));
            enriched.putAll(node.properties());
            enriched.putIfAbsent("uid", uid);
            return new SourceGraphNodeResponse(uid, node.label(), node.name(), enriched);
        });
    }

    private Map<String, Object> relationshipProperties(AnalysisTarget target) {
        Map<String, Object> metadata = ontologyMetadata(target);
        metadata.put("graphKey", target.graphKey());
        if (target.historyId() != null) {
            metadata.put("historyId", target.historyId());
        }
        return metadata;
    }

    private Map<String, Object> ontologyMetadata(AnalysisTarget target) {
        return properties(
                "projectId", target.projectId(),
                "sourceKey", target.source(),
                "fileHash", firstText(target.fileHash(), ""),
                "analysisVersion", SourceOntology.ANALYSIS_VERSION,
                "validFrom", target.indexedAt(),
                "validTo", "",
                "confidence", 1.0d,
                "extractor", SourceOntology.JAVA_EXTRACTOR,
                "evidenceChunkIds", target.evidenceChunkIds(),
                "createdAt", target.indexedAt()
        );
    }

    private String typeKind(TypeDeclaration<?> type) {
        if (type.isClassOrInterfaceDeclaration()) {
            return type.asClassOrInterfaceDeclaration().isInterface() ? "INTERFACE" : "CLASS";
        }
        if (type.isEnumDeclaration()) {
            return "ENUM";
        }
        if (type.isAnnotationDeclaration()) {
            return "ANNOTATION";
        }
        if (type.isRecordDeclaration()) {
            return "RECORD";
        }
        return "UNKNOWN";
    }

    private String inferLayer(String simpleName, TypeDeclaration<?> type) {
        String name = simpleName == null ? "" : simpleName.toLowerCase(Locale.ROOT);
        Set<String> annotations = new HashSet<>();
        if (type != null) {
            type.getAnnotations().forEach(annotation -> annotations.add(annotation.getNameAsString().toLowerCase(Locale.ROOT)));
        }
        if (name.endsWith("controller") || annotations.contains("restcontroller") || annotations.contains("controller")) {
            return "Controller";
        }
        if (name.endsWith("serviceimpl")) {
            return "ServiceImpl";
        }
        if (name.endsWith("service") || annotations.contains("service")) {
            return "Service";
        }
        if (name.endsWith("repository") || annotations.contains("repository")) {
            return "Repository";
        }
        if (name.endsWith("mapper") || annotations.contains("mapper")) {
            return "Mapper";
        }
        if (name.endsWith("dto") || name.endsWith("request") || name.endsWith("response")) {
            return "DTO";
        }
        if (name.endsWith("exception")) {
            return "Exception";
        }
        if (name.endsWith("test")) {
            return "Test Code";
        }
        return "Domain";
    }

    private boolean shouldSkipFqn(String fqn) {
        return !StringUtils.hasText(fqn) || fqn.startsWith("java.") || fqn.startsWith("javax.") || fqn.startsWith("jakarta.");
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String fqn(String packageName, String simpleName) {
        return StringUtils.hasText(packageName) ? packageName + "." + simpleName : simpleName;
    }

    private String simpleName(String fqn) {
        int index = fqn.lastIndexOf('.');
        return index >= 0 ? fqn.substring(index + 1) : fqn;
    }

    private String methodSignature(MethodDeclaration method) {
        String parameters = method.getParameters().stream()
                .map(parameter -> parameter.getType().asString().replaceAll("\\s+", "")
                        + (parameter.isVarArgs() ? "..." : ""))
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return method.getNameAsString() + "(" + parameters + ")";
    }

    private String generationUid(Long historyId) {
        return "generation:" + historyId;
    }

    private String generationProjectId(GenerationHistory history) {
        String projectStructure = firstText(history.getProjectStructure(), "generation");
        UUID stableId = UUID.nameUUIDFromBytes(projectStructure.getBytes(StandardCharsets.UTF_8));
        return "generation-" + stableId;
    }

    private String stringProperty(SourceGraphNodeResponse node, String key) {
        if (node == null || node.properties() == null) {
            return "";
        }
        Object value = node.properties().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private record AnalysisTarget(
            String graphKey,
            String projectId,
            String moduleName,
            String filePath,
            String fileHash,
            List<String> evidenceChunkIds,
            String indexedAt,
            Long historyId,
            String rootLabel,
            String rootUid,
            String rootName,
            String rootToSourceRelationship,
            String sourceKind,
            String source,
            String fileName,
            String targetType,
            String content,
            Map<String, Object> rootProperties
    ) {
    }

    private record RelationshipKey(String sourceId, String targetId, String type) {
    }

    private record EndpointMapping(String httpMethod, String path) {
    }

    private record TableReference(String schema, String tableName) {
        String qualifiedName() {
            return schema + "." + tableName;
        }
    }
}
