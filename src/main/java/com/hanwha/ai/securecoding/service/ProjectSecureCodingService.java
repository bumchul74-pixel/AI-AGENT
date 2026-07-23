package com.hanwha.ai.securecoding.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanwha.ai.chat.config.SecureCodingProperties;
import com.hanwha.ai.document.domain.RagDocument;
import com.hanwha.ai.document.service.DocumentStorageService;
import com.hanwha.ai.document.service.RagDocumentRepository;
import com.hanwha.ai.global.exception.BusinessException;
import com.hanwha.ai.mcp.gateway.AiMcpGatewayService;
import com.hanwha.ai.securecoding.dto.ProjectSecureCodingScanResponse;
import com.hanwha.ai.securecoding.dto.SecureCodingResultRow;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProjectSecureCodingService {
    private static final Set<String> MCP_SUPPORTED_EXTENSIONS = Set.of("java");

    private final RagDocumentRepository documentRepository;
    private final DocumentStorageService documentStorage;
    private final ObjectProvider<AiMcpGatewayService> gatewayProvider;
    private final SecureCodingProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProjectSecureCodingService(
            RagDocumentRepository documentRepository,
            DocumentStorageService documentStorage,
            ObjectProvider<AiMcpGatewayService> gatewayProvider,
            SecureCodingProperties properties
    ) {
        this.documentRepository = documentRepository;
        this.documentStorage = documentStorage;
        this.gatewayProvider = gatewayProvider;
        this.properties = properties;
    }

    public ProjectSecureCodingScanResponse scan(String projectKey) {
        if (!StringUtils.hasText(projectKey) || !documentRepository.projectExists(projectKey.trim())) {
            throw new BusinessException("Project not found.");
        }
        String normalizedProjectKey = projectKey.trim();
        List<RagDocument> documents = documentRepository.findAll(normalizedProjectKey).stream()
                .filter(document -> supportedExtension(document.getOriginalFileName()) != null)
                .toList();
        if (documents.isEmpty()) {
            throw new BusinessException("No uploaded Java source files were found.");
        }

        List<SecureCodingResultRow> rows = new ArrayList<>();
        int scannedFiles = 0;
        int passedFiles = 0;
        int errorFiles = 0;
        for (RagDocument document : documents) {
            FileScanRows fileScan = scanDocument(document);
            rows.addAll(fileScan.rows());
            if (fileScan.error()) errorFiles++;
            else {
                scannedFiles++;
                if (fileScan.passed()) passedFiles++;
            }
        }
        int findingCount = (int) rows.stream().filter(row -> "FINDING".equals(row.status())).count();
        return new ProjectSecureCodingScanResponse(
                normalizedProjectKey, LocalDateTime.now(), documents.size(), scannedFiles,
                passedFiles, findingCount, errorFiles, List.copyOf(rows));
    }

    public List<RagDocument> findScannableDocuments(String projectKey) {
        String normalizedProjectKey = projectKey == null ? "" : projectKey.trim();
        return documentRepository.findAll(normalizedProjectKey).stream()
                .filter(document -> supportedExtension(document.getOriginalFileName()) != null)
                .toList();
    }

    public FileScanRows scanDocument(RagDocument document) {
        String fileType = fileType(document.getOriginalFileName());
        try {
            String source = readUtf8(document);
            McpSchema.CallToolResult toolResult = gateway().callTool("scan_source", Map.of(
                    "fileName", plainFileName(document.getOriginalFileName()),
                    "source", source,
                    "ruleSets", ruleSets(fileType)));
            DownstreamScanResult result = parse(toolResult);
            if (!"COMPLETED".equalsIgnoreCase(result.status())) {
                return FileScanRows.error(errorRow(document, fileType,
                        StringUtils.hasText(result.errorMessage())
                                ? result.errorMessage() : "Semgrep scan failed."));
            }
            if (result.findings().isEmpty()) {
                return FileScanRows.passed(new SecureCodingResultRow(
                        document.getId(), document.getOriginalFileName(), fileType, "PASSED",
                        "-", "-", "No security findings", null, null, null, null));
            }
            List<SecureCodingResultRow> findingRows = result.findings().stream()
                    .map(finding -> new SecureCodingResultRow(
                            document.getId(), document.getOriginalFileName(), fileType, "FINDING",
                            valueOr(finding.severity(), "WARNING"), valueOr(finding.ruleId(), "unknown"),
                            valueOr(finding.message(), "Security finding"), finding.startLine(),
                            finding.startColumn(), finding.endLine(), finding.endColumn()))
                    .toList();
            return FileScanRows.findings(findingRows);
        } catch (RuntimeException exception) {
            return FileScanRows.error(errorRow(document, fileType, rootMessage(exception)));
        }
    }

    private String readUtf8(RagDocument document) {
        long maxBytes = properties.maxFileSize().toBytes();
        if (document.getFileSize() != null && document.getFileSize() > maxBytes) {
            throw new BusinessException("File exceeds the secure coding size limit.");
        }
        try (InputStream input = documentStorage.load(document).getInputStream()) {
            int readLimit = (int) Math.min(Integer.MAX_VALUE - 1L, maxBytes + 1L);
            byte[] content = input.readNBytes(readLimit);
            if (content.length > maxBytes) {
                throw new BusinessException("File exceeds the secure coding size limit.");
            }
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content)).toString();
        } catch (IOException exception) {
            throw new BusinessException("Failed to read the uploaded source file.", exception);
        }
    }

    private DownstreamScanResult parse(McpSchema.CallToolResult result) {
        if (result == null) throw new BusinessException("Semgrep returned no response.");
        if (Boolean.TRUE.equals(result.isError())) {
            throw new BusinessException("Semgrep scan failed: " + textContent(result));
        }
        try {
            if (result.structuredContent() != null) {
                DownstreamScanResult parsed = parseNode(
                        objectMapper.valueToTree(result.structuredContent()));
                if (parsed != null) return parsed;
            }
            String text = textContent(result);
            if (StringUtils.hasText(text)) {
                DownstreamScanResult parsed = parseNode(objectMapper.readTree(text));
                if (parsed != null) return parsed;
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new BusinessException("Semgrep returned an invalid result.", exception);
        }
        throw new BusinessException("Semgrep returned an empty result.");
    }

    private DownstreamScanResult parseNode(JsonNode node) throws IOException {
        if (node == null || node.isNull()) return null;
        if (node.isTextual()) {
            String text = node.asText();
            if (!StringUtils.hasText(text)) return null;
            try {
                return parseNode(objectMapper.readTree(text));
            } catch (IOException exception) {
                return null;
            }
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                DownstreamScanResult parsed = parseNode(item);
                if (parsed != null) return parsed;
            }
            return null;
        }
        if (!node.isObject()) return null;
        if (node.has("status") || node.has("findings")) {
            return objectMapper.treeToValue(node, DownstreamScanResult.class);
        }
        for (String wrapper : List.of("text", "content", "result", "data")) {
            DownstreamScanResult parsed = parseNode(node.get(wrapper));
            if (parsed != null) return parsed;
        }
        return null;
    }

    private String textContent(McpSchema.CallToolResult result) {
        if (result.content() == null) return "";
        return result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(McpSchema.TextContent.class::cast)
                .map(McpSchema.TextContent::text)
                .filter(StringUtils::hasText)
                .findFirst().orElse("");
    }

    private List<String> ruleSets(String fileType) {
        LinkedHashSet<String> configured = new LinkedHashSet<>(
                properties.ruleSets().stream().filter(StringUtils::hasText).toList());
        if (!"JAVA".equals(fileType) && !configured.isEmpty()) configured.add("sql-security");
        return List.copyOf(configured);
    }

    private AiMcpGatewayService gateway() {
        return gatewayProvider.getIfAvailable(() -> {
            throw new BusinessException("AI-MCP is disabled. Check MCP_CLIENT_ENABLED.");
        });
    }

    private SecureCodingResultRow errorRow(RagDocument document, String fileType, String message) {
        return new SecureCodingResultRow(document.getId(), document.getOriginalFileName(), fileType,
                "ERROR", "-", "-", valueOr(message, "Scan failed"), null, null, null, null);
    }

    private String supportedExtension(String fileName) {
        String extension = extension(fileName);
        boolean configured = properties.allowedExtensions().stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(extension::equals);
        return configured && MCP_SUPPORTED_EXTENSIONS.contains(extension) ? extension : null;
    }

    public String fileType(String fileName) {
        return switch (extension(fileName)) {
            case "java" -> "JAVA";
            case "sql" -> "SQL";
            default -> "MYBATIS_XML";
        };
    }

    private String extension(String fileName) {
        // Extension normalization is shared by synchronous and queued scans.
        String value = fileName == null ? "" : fileName.trim().toLowerCase(Locale.ROOT);
        int dot = value.lastIndexOf('.');
        return dot < 0 ? "" : value.substring(dot + 1);
    }

    private String plainFileName(String fileName) {
        return Path.of(fileName.replace('\\', '/')).getFileName().toString();
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return valueOr(current.getMessage(), "Scan failed");
    }

    private String valueOr(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DownstreamScanResult(
            String status, List<DownstreamFinding> findings, String errorMessage) {
        private DownstreamScanResult {
            findings = findings == null ? List.of() : List.copyOf(findings);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DownstreamFinding(
            String ruleId, String message, String severity, String path,
            Integer startLine, Integer startColumn, Integer endLine, Integer endColumn) {
    }

    public record FileScanRows(List<SecureCodingResultRow> rows, boolean passed, boolean error) {
        private static FileScanRows passed(SecureCodingResultRow row) {
            return new FileScanRows(List.of(row), true, false);
        }
        private static FileScanRows findings(List<SecureCodingResultRow> rows) {
            return new FileScanRows(rows, false, false);
        }
        private static FileScanRows error(SecureCodingResultRow row) {
            return new FileScanRows(List.of(row), false, true);
        }
    }
}
