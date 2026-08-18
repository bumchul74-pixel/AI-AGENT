package com.hanwha.ai.sourcequality.service;

import com.hanwha.ai.sourcegraph.config.SourceGraphProperties;
import com.hanwha.ai.sourcequality.domain.SourceQualityMethod;
import com.hanwha.ai.sourcequality.domain.SourceQualityMethodDetail;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

@Component
public class Neo4jSourceQualityGraphReader implements SourceQualityGraphReader {
    private final Neo4jClient neo4jClient;
    private final SourceGraphProperties properties;

    public Neo4jSourceQualityGraphReader(Neo4jClient neo4jClient, SourceGraphProperties properties) {
        this.neo4jClient = neo4jClient;
        this.properties = properties;
    }

    @Override
    public List<SourceQualityMethod> findMethods(String projectKey) {
        if (!properties.enabled()) return List.of();
        Collection<Map<String, Object>> rows = neo4jClient.query("""
                MATCH (method:Method)
                WHERE method.projectId = $projectKey
                  AND method.methodUid IS NOT NULL
                  AND method.methodHash IS NOT NULL
                RETURN method.methodUid AS methodUid,
                       method.declaringType AS declaringType,
                       method.signature AS signature,
                       method.filePath AS filePath,
                       method.startLine AS startLine,
                       method.endLine AS endLine,
                       method.lineCount AS lineCount,
                       method.methodHash AS methodHash,
                       method.structuralHash AS structuralHash,
                       method.cyclomaticComplexity AS cyclomaticComplexity,
                       method.cognitiveComplexity AS cognitiveComplexity,
                       method.maxNestingDepth AS maxNestingDepth,
                       method.parameterCount AS parameterCount,
                       method.returnCount AS returnCount,
                       method.throwCount AS throwCount,
                       method.branchCount AS branchCount,
                       method.callCount AS callCount
                ORDER BY method.cyclomaticComplexity DESC,
                         method.cognitiveComplexity DESC,
                         method.methodUid
                """).bind(projectKey).to("projectKey").fetch().all();
        return rows.stream().map(this::toMethod).toList();
    }

    @Override
    public List<SourceQualityMethodDetail> findDuplicateMethods(String projectKey, String type, String hash) {
        if (!properties.enabled()) return List.of();
        String hashProperty = "EXACT".equals(type) ? "method.methodHash" : "method.structuralHash";
        Collection<Map<String, Object>> rows = neo4jClient.query("""
                MATCH (method:Method)
                WHERE method.projectId = $projectKey
                  AND %s = $hash
                RETURN method.methodUid AS methodUid,
                       method.declaringType AS declaringType,
                       method.signature AS signature,
                       method.filePath AS filePath,
                       method.startLine AS startLine,
                       method.endLine AS endLine,
                       method.lineCount AS lineCount,
                       method.methodHash AS methodHash,
                       method.structuralHash AS structuralHash,
                       method.cyclomaticComplexity AS cyclomaticComplexity,
                       method.cognitiveComplexity AS cognitiveComplexity,
                       method.maxNestingDepth AS maxNestingDepth,
                       method.parameterCount AS parameterCount,
                       method.returnCount AS returnCount,
                       method.throwCount AS throwCount,
                       method.branchCount AS branchCount,
                       method.callCount AS callCount,
                       method.methodBody AS methodBody
                ORDER BY method.filePath, method.startLine, method.methodUid
                """.formatted(hashProperty))
                .bind(projectKey).to("projectKey")
                .bind(hash).to("hash")
                .fetch().all();
        return rows.stream()
                .map(row -> new SourceQualityMethodDetail(toMethod(row), text(row.get("methodBody"))))
                .toList();
    }

    @Override
    public Optional<SourceQualityMethodDetail> findMethodDetail(String projectKey, String methodUid) {
        if (!properties.enabled()) return Optional.empty();
        return neo4jClient.query("""
                MATCH (method:Method)
                WHERE method.projectId = $projectKey
                  AND method.methodUid = $methodUid
                RETURN method.methodUid AS methodUid,
                       method.declaringType AS declaringType,
                       method.signature AS signature,
                       method.filePath AS filePath,
                       method.startLine AS startLine,
                       method.endLine AS endLine,
                       method.lineCount AS lineCount,
                       method.methodHash AS methodHash,
                       method.structuralHash AS structuralHash,
                       method.cyclomaticComplexity AS cyclomaticComplexity,
                       method.cognitiveComplexity AS cognitiveComplexity,
                       method.maxNestingDepth AS maxNestingDepth,
                       method.parameterCount AS parameterCount,
                       method.returnCount AS returnCount,
                       method.throwCount AS throwCount,
                       method.branchCount AS branchCount,
                       method.callCount AS callCount,
                       method.methodBody AS methodBody
                LIMIT 1
                """)
                .bind(projectKey).to("projectKey")
                .bind(methodUid).to("methodUid")
                .fetch().one()
                .map(row -> new SourceQualityMethodDetail(toMethod(row), text(row.get("methodBody"))));
    }

    private SourceQualityMethod toMethod(Map<String, Object> row) {
        return new SourceQualityMethod(
                text(row.get("methodUid")), text(row.get("declaringType")),
                text(row.get("signature")), text(row.get("filePath")),
                number(row.get("startLine")), number(row.get("endLine")), number(row.get("lineCount")),
                text(row.get("methodHash")), text(row.get("structuralHash")),
                number(row.get("cyclomaticComplexity")), number(row.get("cognitiveComplexity")),
                number(row.get("maxNestingDepth")), number(row.get("parameterCount")),
                number(row.get("returnCount")), number(row.get("throwCount")),
                number(row.get("branchCount")), number(row.get("callCount")));
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private int number(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return value == null ? 0 : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return 0; }
    }
}
