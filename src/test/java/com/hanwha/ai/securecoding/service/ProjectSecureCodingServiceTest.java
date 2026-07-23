package com.hanwha.ai.securecoding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hanwha.ai.chat.config.SecureCodingProperties;
import com.hanwha.ai.document.domain.RagDocument;
import com.hanwha.ai.document.service.DocumentStorageService;
import com.hanwha.ai.document.service.RagDocumentRepository;
import com.hanwha.ai.mcp.gateway.AiMcpGatewayService;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.unit.DataSize;

class ProjectSecureCodingServiceTest {

    @Test
    void scansProjectSourcesAndReturnsFindingAndPassedRows() {
        RagDocumentRepository repository = mock(RagDocumentRepository.class);
        DocumentStorageService storage = mock(DocumentStorageService.class);
        RagDocument javaDocument = document(1L, "src/UserService.java");
        RagDocument sqlDocument = document(2L, "mapper/UserMapper.xml");
        RagDocument ignoredDocument = document(3L, "README.md");
        when(repository.projectExists("sample")).thenReturn(true);
        when(repository.findAll("sample")).thenReturn(List.of(javaDocument, sqlDocument, ignoredDocument));
        when(storage.load(javaDocument)).thenReturn(new ByteArrayResource(
                "class UserService {}".getBytes(StandardCharsets.UTF_8)));
        when(storage.load(sqlDocument)).thenReturn(new ByteArrayResource(
                "<select>SELECT * FROM users WHERE id = ${id}</select>".getBytes(StandardCharsets.UTF_8)));

        List<Map<String, Object>> calls = new ArrayList<>();
        AiMcpGatewayService gateway = mock(AiMcpGatewayService.class);
        when(gateway.callTool(eq("scan_source"), any())).thenAnswer(invocation -> {
            Map<String, Object> arguments = invocation.getArgument(1);
            calls.add(arguments);
            String wrapped = "[{\"text\":\"{\\\"status\\\":\\\"COMPLETED\\\"," +
                    "\\\"findings\\\":[{\\\"ruleId\\\":\\\"java-security.sql-injection\\\"," +
                    "\\\"message\\\":\\\"Unsafe query\\\",\\\"severity\\\":\\\"ERROR\\\"," +
                    "\\\"startLine\\\":12,\\\"startColumn\\\":5,\\\"endLine\\\":12," +
                    "\\\"endColumn\\\":20}],\\\"errorMessage\\\":\\\"\\\"}\"}]";
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent(wrapped)), false, null, Map.of());
        });

        ProjectSecureCodingService service = new ProjectSecureCodingService(
                repository, storage, provider(gateway),
                new SecureCodingProperties(DataSize.ofMegabytes(1),
                        List.of("java", "sql", "xml"), List.of()));

        var response = service.scan("sample");

        assertThat(response.totalFiles()).isEqualTo(1);
        assertThat(response.scannedFiles()).isEqualTo(1);
        assertThat(response.findingCount()).isEqualTo(1);
        assertThat(response.passedFiles()).isZero();
        assertThat(response.errorFiles()).isZero();
        assertThat(response.results()).extracting("status").containsExactly("FINDING");
        assertThat(calls).hasSize(1);
        assertThat(calls).extracting(call -> call.get("fileName"))
                .containsExactly("UserService.java");
    }

    @Test
    void keepsScanningWhenOneDocumentFails() {
        RagDocumentRepository repository = mock(RagDocumentRepository.class);
        DocumentStorageService storage = mock(DocumentStorageService.class);
        RagDocument broken = document(1L, "Broken.java");
        when(repository.projectExists("sample")).thenReturn(true);
        when(repository.findAll("sample")).thenReturn(List.of(broken));
        when(storage.load(broken)).thenThrow(new IllegalStateException("missing source"));

        ProjectSecureCodingService service = new ProjectSecureCodingService(
                repository, storage, provider(mock(AiMcpGatewayService.class)),
                new SecureCodingProperties(DataSize.ofMegabytes(1), List.of(), List.of()));

        var response = service.scan("sample");

        assertThat(response.errorFiles()).isEqualTo(1);
        assertThat(response.results()).singleElement().satisfies(row -> {
            assertThat(row.status()).isEqualTo("ERROR");
            assertThat(row.message()).isEqualTo("missing source");
        });
    }

    private ObjectProviderFixture provider(AiMcpGatewayService gateway) {
        return new ObjectProviderFixture(gateway);
    }

    private RagDocument document(Long id, String fileName) {
        RagDocument document = new RagDocument();
        document.setId(id);
        document.setProjectKey("sample");
        document.setOriginalFileName(fileName);
        document.setFileSize(100L);
        return document;
    }

    private static final class ObjectProviderFixture
            implements org.springframework.beans.factory.ObjectProvider<AiMcpGatewayService> {
        private final AiMcpGatewayService gateway;
        private ObjectProviderFixture(AiMcpGatewayService gateway) { this.gateway = gateway; }
        @Override public AiMcpGatewayService getObject(Object... args) { return gateway; }
        @Override public AiMcpGatewayService getIfAvailable() { return gateway; }
        @Override public AiMcpGatewayService getIfUnique() { return gateway; }
        @Override public AiMcpGatewayService getObject() { return gateway; }
    }
}
