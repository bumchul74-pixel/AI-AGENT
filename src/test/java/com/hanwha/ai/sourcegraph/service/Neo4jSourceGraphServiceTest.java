package com.hanwha.ai.sourcegraph.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hanwha.ai.document.domain.RagDocument;
import com.hanwha.ai.document.service.RagDocumentRepository;
import com.hanwha.ai.generation.repository.GenerationRepository;
import com.hanwha.ai.sourcegraph.config.SourceGraphProperties;
import com.hanwha.ai.sourcegraph.dto.SourceGraphNodeSourceResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.neo4j.core.Neo4jClient;

class Neo4jSourceGraphServiceTest {
    @TempDir
    Path tempDirectory;

    @Test
    void loadsXmlSourceFileContentUsingStructuredGraphSourceKey() throws Exception {
        String nodeId = "file:commerce:src/main/resources/mapper/UserMapper.xml";
        String sourceKey = "document:100";
        String xml = """
                <mapper namespace="com.example.UserMapper">
                  <select id="findAll">SELECT * FROM users</select>
                </mapper>
                """;
        Path xmlFile = tempDirectory.resolve("UserMapper.xml");
        Files.writeString(xmlFile, xml, StandardCharsets.UTF_8);

        Neo4jClient neo4jClient = mock(Neo4jClient.class);
        Neo4jClient.UnboundRunnableSpec query = mock(Neo4jClient.UnboundRunnableSpec.class);
        @SuppressWarnings("unchecked")
        Neo4jClient.OngoingBindSpec<String, Neo4jClient.RunnableSpec> binding =
                mock(Neo4jClient.OngoingBindSpec.class);
        @SuppressWarnings("unchecked")
        Neo4jClient.RecordFetchSpec<Map<String, Object>> fetch =
                mock(Neo4jClient.RecordFetchSpec.class);

        when(neo4jClient.query(anyString())).thenReturn(query);
        when(query.bind(nodeId)).thenReturn(binding);
        when(binding.to("nodeId")).thenReturn(query);
        when(query.fetch()).thenReturn(fetch);
        Collection<Map<String, Object>> rows = List.of(Map.of(
                "nodeId", nodeId,
                "label", "SourceFile",
                "name", "UserMapper.xml",
                "nodeProperties", Map.of(
                        "sourceKey", sourceKey,
                        "fileName", "UserMapper.xml",
                        "filePath", "src/main/resources/mapper/UserMapper.xml",
                        "sourceKind", "mybatis-xml"
                )
        ));
        when(fetch.all()).thenReturn(rows);

        RagDocument document = new RagDocument();
        document.setOriginalFileName("UserMapper.xml");
        document.setStoredFileName("stored.xml");
        document.setFilePath(xmlFile.toString());
        RagDocumentRepository documentRepository = mock(RagDocumentRepository.class);
        when(documentRepository.findByGraphSourceKey(sourceKey)).thenReturn(document);

        Neo4jSourceGraphService service = new Neo4jSourceGraphService(
                neo4jClient,
                new SourceGraphProperties(true),
                mock(JavaSourceGraphAnalyzer.class),
                mock(GenerationRepository.class),
                documentRepository
        );

        SourceGraphNodeSourceResponse response = service.findNodeSource(nodeId);

        assertThat(response.available()).isTrue();
        assertThat(response.fileName()).isEqualTo("UserMapper.xml");
        assertThat(response.content()).isEqualTo(xml);
        assertThat(response.filePath()).isEqualTo(xmlFile.toAbsolutePath().normalize().toString());
        verify(documentRepository).findByGraphSourceKey(sourceKey);
    }
}
