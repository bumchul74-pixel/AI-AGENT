package com.hanwha.ai.document.workflow.task;

import com.hanwha.ai.document.config.DocumentProperties;
import com.hanwha.ai.document.domain.DocumentFileSupport;
import com.hanwha.ai.document.domain.IndexStatus;
import com.hanwha.ai.document.dto.PythonDocumentIngestResponse;
import com.hanwha.ai.document.dto.VectorChunkIngestRequest;
import com.hanwha.ai.document.service.PythonDocumentIngestClient;
import com.hanwha.ai.document.service.RagDocumentRepository;
import com.hanwha.ai.document.workflow.DocumentIndexContext;
import com.hanwha.ai.document.workflow.DocumentIndexTask;
import com.hanwha.ai.document.workflow.DocumentIndexWorkflow;
import com.hanwha.ai.global.exception.BusinessException;
import com.hanwha.ai.sourcegraph.domain.SourceGraphIdentity;
import com.hanwha.ai.sourcegraph.dto.JavaSourceGraphIngestRequest;
import com.hanwha.ai.sourcegraph.service.StructuredSourceGraphAnalyzer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(40)
public class RagVectorIngestTask implements DocumentIndexTask {
    private final StructuredSourceGraphAnalyzer structuredAnalyzer = new StructuredSourceGraphAnalyzer();
    private final PythonDocumentIngestClient ingestClient;
    private final DocumentProperties properties;
    private final RagDocumentRepository repository;

    public RagVectorIngestTask(
            PythonDocumentIngestClient ingestClient,
            DocumentProperties properties,
            RagDocumentRepository repository
    ) {
        this.ingestClient = ingestClient;
        this.properties = properties;
        this.repository = repository;
    }

    @Override
    public void execute(DocumentIndexContext context) {
        if (!context.isSupportedVectorFile()) {
            String message = "Unsupported document file type for VectorDB indexing. supported="
                    + DocumentFileSupport.supportedExtensionsDescription();
            repository.updateVectorIndexResult(context.document().getId(), IndexStatus.FAILED.name(), 0, message);
            throw new BusinessException(message);
        }

        try {
            PythonDocumentIngestResponse response = ingestClient.ingest(
                    context.filePath(),
                    context.vectorSourceKey(),
                    properties.chunkSize(),
                    properties.overlap(),
                    context.document().getProjectKey(),
                    context.logicalFilePath(),
                    context.document().getFileHash(),
                    List.of(SourceGraphIdentity.sourceFileUid(
                            context.document().getProjectKey(),
                            context.logicalFilePath()
                    )),
                    context.document().getId(),
                    sourceSymbol(context.document().getOriginalFileName()),
                    vectorMetadata(context)
            );
            List<String> baseChunkIds = java.util.stream.IntStream.range(0, response.storedCount())
                    .mapToObj(index -> context.vectorSourceKey() + ":chunk:" + index)
                    .toList();
            context.setStoredChunkIds(baseChunkIds);
            int statementChunkCount = ingestMyBatisStatementChunks(context);
            int totalChunkCount = response.storedCount() + statementChunkCount;
            context.setStoredChunkCount(totalChunkCount);
            repository.updateVectorIndexResult(
                    context.document().getId(),
                    IndexStatus.INDEXED.name(),
                    totalChunkCount,
                    null
            );
            context.document().setVectorIndexStatus(IndexStatus.INDEXED.name());
            context.document().setChunkCount(totalChunkCount);
        } catch (Exception exception) {
            String message = DocumentIndexWorkflow.rootMessage(exception);
            repository.updateVectorIndexResult(context.document().getId(), IndexStatus.FAILED.name(), 0, message);
            throw toRuntimeException(exception);
        }
    }

    private int ingestMyBatisStatementChunks(DocumentIndexContext context) throws Exception {
        String fileName = context.document().getOriginalFileName();
        if (fileName == null || !fileName.toLowerCase().endsWith(".xml")) {
            return 0;
        }
        String content = Files.readString(context.filePath(), StandardCharsets.UTF_8);
        JavaSourceGraphIngestRequest analysisRequest = new JavaSourceGraphIngestRequest(
                context.graphSourceKey(), fileName, content,
                context.document().getProjectKey(), properties.moduleName(), context.logicalFilePath(),
                context.document().getFileHash(), List.of()
        );
        List<StructuredSourceGraphAnalyzer.MyBatisStatementAnalysis> statements =
                structuredAnalyzer.analyzeMyBatisStatements(analysisRequest);
        if (statements.isEmpty()) {
            return 0;
        }

        String sourceFileUid = SourceGraphIdentity.sourceFileUid(
                context.document().getProjectKey(), context.logicalFilePath());
        List<VectorChunkIngestRequest.VectorChunk> chunks = new ArrayList<>();
        for (StructuredSourceGraphAnalyzer.MyBatisStatementAnalysis statement : statements) {
            String chunkId = StructuredSourceGraphAnalyzer.statementChunkId(
                    context.vectorSourceKey(), statement.statementId());
            String mapperUid = SourceGraphIdentity.typeUid(context.document().getProjectKey(), statement.namespace());
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("contentType", "mybatis-statement");
            metadata.put("namespace", statement.namespace());
            metadata.put("statementId", statement.statementId());
            metadata.put("commandType", statement.commandType());
            metadata.put("operation", statement.operation());
            metadata.put("dynamic", statement.dynamic());
            metadata.put("conditions", statement.conditions());
            metadata.put("sqlVariants", statement.sqlVariants());
            chunks.add(new VectorChunkIngestRequest.VectorChunk(
                    chunkId,
                    context.vectorSourceKey(),
                    statementChunkContent(statement),
                    context.document().getId(),
                    context.document().getProjectKey(),
                    context.logicalFilePath(),
                    context.document().getFileHash(),
                    List.of(statement.statementUid(), mapperUid, sourceFileUid),
                    statement.namespace() + "." + statement.statementId(),
                    metadata
            ));
        }
        PythonDocumentIngestResponse response = ingestClient.ingestChunks(new VectorChunkIngestRequest(chunks));
        context.addStoredChunkIds(chunks.stream().map(VectorChunkIngestRequest.VectorChunk::chunkId).toList());
        return response.storedCount();
    }

    private String statementChunkContent(StructuredSourceGraphAnalyzer.MyBatisStatementAnalysis statement) {
        StringBuilder content = new StringBuilder()
                .append("MyBatis Mapper: ").append(statement.namespace()).append('\n')
                .append("Statement: ").append(statement.statementId()).append('\n')
                .append("Command: ").append(statement.commandType()).append('\n')
                .append("SQL Template:\n").append(statement.sqlTemplate());
        if (!statement.conditions().isEmpty()) {
            content.append("\nDynamic Conditions:\n- ")
                    .append(String.join("\n- ", statement.conditions()));
        }
        if (statement.sqlVariants().size() > 1) {
            content.append("\nSQL Variants:\n- ")
                    .append(String.join("\n- ", statement.sqlVariants()));
        }
        return content.toString();
    }

    private Map<String, Object> vectorMetadata(DocumentIndexContext context) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("moduleName", properties.moduleName());
        metadata.put("projectKey", context.document().getProjectKey());
        putIfPresent(metadata, "documentType", context.document().getDocumentType());
        putIfPresent(metadata, "contentType", context.document().getContentType());
        return metadata;
    }

    private void putIfPresent(Map<String, Object> metadata, String key, String value) {
        if (value != null && !value.isBlank()) {
            metadata.put(key, value);
        }
    }

    private String sourceSymbol(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        int extensionIndex = fileName.lastIndexOf('.');
        return extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
    }

    private RuntimeException toRuntimeException(Exception exception) {
        if (exception instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException(exception);
    }
}
