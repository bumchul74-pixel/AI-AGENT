package com.hanwha.ai.sourcegraph.service;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.hanwha.ai.generation.domain.GenerationHistory;
import com.hanwha.ai.sourcegraph.dto.JavaSourceGraphIngestRequest;
import com.hanwha.ai.sourcegraph.dto.SourceGraphNodeResponse;
import com.hanwha.ai.sourcegraph.dto.SourceGraphRelationshipResponse;
import com.hanwha.ai.sourcegraph.dto.SourceGraphResponse;
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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class JavaSourceGraphAnalyzer {
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(?:java)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Set<String> COMMON_TYPES = Set.of(
            "String", "Boolean", "Byte", "Short", "Integer", "Long", "Float", "Double", "Character",
            "Object", "List", "Set", "Map", "Collection", "Optional", "Page", "Pageable", "ResponseEntity",
            "LocalDate", "LocalDateTime", "BigDecimal", "BigInteger", "Void"
    );

    public SourceGraphResponse analyze(GenerationHistory history) {
        if (history == null || history.getId() == null) {
            throw new IllegalArgumentException("Generation history id is required for source graph indexing.");
        }

        Long historyId = history.getId();
        return analyze(new AnalysisTarget(
                "generation:" + historyId,
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
                        "targetType", history.getTargetType(),
                        "sourceKind", "generation-history",
                        "createdAt", history.getCreatedAt()
                )
        ));
    }

    public SourceGraphResponse analyzeJavaSource(JavaSourceGraphIngestRequest request) {
        if (request == null || !StringUtils.hasText(request.content())) {
            throw new IllegalArgumentException("Java source content is required.");
        }

        String source = firstText(request.source(), request.fileName(), "rag-inbox-java-source");
        String fileName = firstText(request.fileName(), source, "Source.java");
        String graphKey = "rag-source:" + UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
        String rootUid = "rag-source:" + graphKey;

        return analyze(new AnalysisTarget(
                graphKey,
                null,
                "RagSource",
                rootUid,
                fileName,
                "INGESTED",
                "rag-inbox-java",
                source,
                fileName,
                null,
                request.content(),
                properties(
                        "uid", rootUid,
                        "graphKey", graphKey,
                        "sourceKind", "rag-inbox-java",
                        "source", source,
                        "fileName", fileName,
                        "name", fileName
                )
        ));
    }

    private SourceGraphResponse analyze(AnalysisTarget target) {
        Map<String, SourceGraphNodeResponse> nodes = new LinkedHashMap<>();
        Set<RelationshipKey> relationships = new LinkedHashSet<>();

        addNode(nodes, target.rootUid(), target.rootLabel(), target.rootName(), target.rootProperties());

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

            for (TypeDeclaration<?> type : compilationUnit.getTypes()) {
                String simpleName = type.getNameAsString();
                String fqn = fqn(packageName, simpleName);
                String sourceFileName = firstText(target.fileName(), simpleName + ".java");
                String sourceUid = sourceFileUid(target.graphKey(), fqn, index);
                String typeUid = typeUid(target.graphKey(), fqn);
                String typeKind = typeKind(type);
                String layer = inferLayer(simpleName, type);

                addNode(nodes, sourceUid, "SourceFile", sourceFileName, properties(
                        "uid", sourceUid,
                        "graphKey", target.graphKey(),
                        "historyId", target.historyId(),
                        "sourceKind", target.sourceKind(),
                        "source", target.source(),
                        "fileName", sourceFileName,
                        "packageName", packageName,
                        "primaryType", fqn
                ));
                addRelationship(relationships, target.rootUid(), sourceUid, target.rootToSourceRelationship(), target);

                addJavaTypeNode(nodes, target, typeUid, fqn, simpleName, typeKind, layer, false);
                addRelationship(relationships, sourceUid, typeUid, "DECLARES", target);

                addImports(nodes, relationships, target, typeUid, compilationUnit);
                addInheritance(nodes, relationships, target, typeUid, packageName, importMap, type);
                addFields(nodes, relationships, target, typeUid, packageName, importMap, type);
                addConstructors(nodes, relationships, target, typeUid, packageName, importMap, type);
                addMethods(nodes, relationships, target, typeUid, packageName, importMap, type);
            }
        }

        if (parsedSources == 0) {
            throw new NoJavaTypeFoundException("Java source did not contain parsable Java code.");
        }

        return new SourceGraphResponse(
                target.historyId(),
                List.copyOf(nodes.values()),
                relationships.stream()
                        .map(key -> new SourceGraphRelationshipResponse(
                                key.sourceId(),
                                key.targetId(),
                                key.type(),
                                properties("graphKey", target.graphKey(), "historyId", target.historyId())
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
            String targetUid = typeUid(target.graphKey(), importFqn);
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
        for (FieldDeclaration field : type.findAll(FieldDeclaration.class)) {
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
        for (MethodDeclaration method : type.getMethods()) {
            String methodUid = sourceTypeUid + "#" + methodSignature(method);
            addNode(nodes, methodUid, "Method", method.getNameAsString(), properties(
                    "uid", methodUid,
                    "graphKey", target.graphKey(),
                    "historyId", target.historyId(),
                    "sourceKind", target.sourceKind(),
                    "source", target.source(),
                    "name", method.getNameAsString(),
                    "signature", methodSignature(method)
            ));
            addRelationship(relationships, sourceTypeUid, methodUid, "HAS_METHOD", target);
            addTypeRelationship(nodes, relationships, target, sourceTypeUid, packageName, importMap, method.getType(), "USES");
            method.getParameters().forEach(parameter -> addTypeRelationship(
                    nodes, relationships, target, sourceTypeUid, packageName, importMap, parameter.getType(), "USES"
            ));
            method.getThrownExceptions().forEach(thrownType -> addTypeRelationship(
                    nodes, relationships, target, sourceTypeUid, packageName, importMap, thrownType, "USES"
            ));
        }
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
            String targetUid = typeUid(target.graphKey(), targetFqn);
            addJavaTypeNode(nodes, target, targetUid, targetFqn, simpleName(targetFqn), "UNKNOWN", inferLayer(simpleName(targetFqn), null), true);
            if (!targetUid.equals(sourceTypeUid)) {
                addRelationship(relationships, sourceTypeUid, targetUid, relationshipType, target);
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
                .map(parameter -> parameter.getType().asString())
                .toList()
                .toString();
        return method.getNameAsString() + parameters;
    }

    private String generationUid(Long historyId) {
        return "generation:" + historyId;
    }

    private String sourceFileUid(String graphKey, String fqn, int sourceIndex) {
        return "source:" + graphKey + ":" + sourceIndex + ":" + fqn;
    }

    private String typeUid(String graphKey, String fqn) {
        return "type:" + graphKey + ":" + fqn;
    }

    private record AnalysisTarget(
            String graphKey,
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
}