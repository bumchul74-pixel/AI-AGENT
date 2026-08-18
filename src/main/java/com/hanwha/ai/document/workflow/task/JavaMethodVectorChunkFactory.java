package com.hanwha.ai.document.workflow.task;

import com.hanwha.ai.document.dto.VectorChunkIngestRequest;
import com.hanwha.ai.sourcegraph.domain.SourceGraphIdentity;
import com.hanwha.ai.sourcegraph.dto.JavaSourceGraphIngestRequest;
import com.hanwha.ai.sourcegraph.dto.SourceGraphNodeResponse;
import com.hanwha.ai.sourcegraph.service.JavaSourceGraphAnalyzer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JavaMethodVectorChunkFactory {
    private static final List<String> QUALITY_METADATA_KEYS = List.of(
            "startLine", "endLine", "lineCount", "methodBody", "normalizedBody",
            "methodHash", "structuralHash", "cyclomaticComplexity", "cognitiveComplexity",
            "maxNestingDepth", "parameterCount", "returnCount", "throwCount",
            "branchCount", "callCount"
    );

    private final JavaSourceGraphAnalyzer analyzer;

    public JavaMethodVectorChunkFactory(JavaSourceGraphAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    public List<VectorChunkIngestRequest.VectorChunk> create(
            JavaSourceGraphIngestRequest request,
            String vectorSourceKey,
            Long documentId
    ) {
        if (request == null || vectorSourceKey == null || vectorSourceKey.isBlank()) {
            return List.of();
        }

        String projectId = SourceGraphIdentity.projectId(request.projectId());
        String sourceFileUid = SourceGraphIdentity.sourceFileUid(projectId, request.filePath());
        List<VectorChunkIngestRequest.VectorChunk> chunks = new ArrayList<>();

        for (SourceGraphNodeResponse node : analyzer.analyzeJavaSource(request).nodes()) {
            Map<String, Object> properties = node.properties();
            if (!"Method".equals(node.label())
                    || properties == null
                    || !"java-method".equals(properties.get("contentType"))) {
                continue;
            }

            String methodUid = text(properties, "methodUid", node.id());
            String declaringType = text(properties, "declaringType", "");
            String signature = text(properties, "signature", node.name());
            String methodBody = text(properties, "methodBody", "");
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("contentType", "java-method");
            metadata.put("methodUid", methodUid);
            metadata.put("declaringType", declaringType);
            metadata.put("signature", signature);
            metadata.put("moduleName", text(properties, "moduleName", request.moduleName()));
            for (String key : QUALITY_METADATA_KEYS) {
                Object value = properties.get(key);
                if (value != null) {
                    metadata.put(key, value);
                }
            }

            List<String> entityIds = new ArrayList<>();
            entityIds.add(methodUid);
            if (!declaringType.isBlank()) {
                entityIds.add(SourceGraphIdentity.typeUid(projectId, declaringType));
            }
            entityIds.add(sourceFileUid);

            chunks.add(new VectorChunkIngestRequest.VectorChunk(
                    vectorSourceKey + ":java-method:" + sha256(methodUid),
                    vectorSourceKey,
                    methodChunkContent(declaringType, signature, methodBody),
                    documentId,
                    projectId,
                    request.filePath(),
                    request.fileHash(),
                    entityIds,
                    declaringType + "." + signature,
                    metadata
            ));
        }
        return List.copyOf(chunks);
    }

    private String methodChunkContent(String declaringType, String signature, String methodBody) {
        String symbol = declaringType.isBlank() ? signature : declaringType + "." + signature;
        return "Java Method: " + symbol + System.lineSeparator() + methodBody;
    }

    private String text(Map<String, Object> properties, String key, String fallback) {
        Object value = properties.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
