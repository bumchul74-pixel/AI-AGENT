package com.hanwha.ai.document.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanwha.ai.document.domain.DocumentType;
import com.hanwha.ai.document.domain.RagDocument;
import com.hanwha.ai.document.dto.DocumentResponse;
import com.hanwha.ai.document.workflow.DocumentIndexWorkflow;
import com.hanwha.ai.knowledge.project.dto.KnowledgeProjectRequest;
import com.hanwha.ai.knowledge.project.service.KnowledgeProjectService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.ai.mcp.client.enabled=false")
@Transactional
class DocumentServiceTest {
    private static final Path STORAGE_DIRECTORY = Path.of(
            "build",
            "test-uploads",
            "document-service-context"
    ).toAbsolutePath().normalize();

    @Autowired
    private DocumentService documentService;

    @Autowired
    private RagDocumentRepository repository;

    @Autowired
    private RecordingDocumentIndexWorkflow indexWorkflow;

    @Autowired
    private KnowledgeProjectService projectService;

    @DynamicPropertySource
    static void documentProperties(DynamicPropertyRegistry registry) {
        registry.add("document.storage-directory", () -> STORAGE_DIRECTORY.toString());
    }

    @BeforeEach
    void setUp() throws IOException {
        indexWorkflow.reset();
        deleteRecursively(STORAGE_DIRECTORY);
        Files.createDirectories(STORAGE_DIRECTORY);
    }

    @AfterEach
    void tearDown() throws IOException {
        deleteRecursively(STORAGE_DIRECTORY);
    }

    @Test
    void uploadStoresPhysicalFileAndRdbRowThenRunsIndexWorkflow() throws IOException {
        Path sourceFile = Files.createTempFile("api-standard-", ".md");
        Files.writeString(sourceFile, "# API Standard\nUse REST naming conventions.", StandardCharsets.UTF_8);

        MockMultipartFile multipartFile;
        try (InputStream inputStream = Files.newInputStream(sourceFile)) {
            multipartFile = new MockMultipartFile(
                    "file",
                    "api-standard.md",
                    "text/markdown",
                    inputStream
            );
        }

        DocumentResponse response = documentService.upload(multipartFile, "STANDARD_DOCUMENT");

        RagDocument savedDocument = repository.findById(response.id());
        Path storedPath = Path.of(savedDocument.getFilePath()).toAbsolutePath().normalize();

        assertThat(savedDocument).isNotNull();
        assertThat(savedDocument.getOriginalFileName()).isEqualTo("api-standard.md");
        assertThat(savedDocument.getDocumentType()).isEqualTo(DocumentType.STANDARD_DOCUMENT.name());
        assertThat(savedDocument.getStoredFileName()).endsWith(".md");
        assertThat(storedPath).startsWith(STORAGE_DIRECTORY);
        assertThat(storedPath).exists().isRegularFile();
        assertThat(Files.readString(storedPath, StandardCharsets.UTF_8))
                .isEqualTo("# API Standard\nUse REST naming conventions.");

        RagDocument workflowDocument = indexWorkflow.lastDocument();
        assertThat(workflowDocument).isNotNull();
        assertThat(workflowDocument.getId()).isEqualTo(savedDocument.getId());
        assertThat(workflowDocument.getFilePath()).isEqualTo(savedDocument.getFilePath());
    }

    @Test
    void repeatedUploadOfSameContentReturnsTheExistingDocument() {
        MockMultipartFile first = new MockMultipartFile(
                "file", "UserInfoInterceptor.java", "text/x-java-source",
                "public class UserInfoInterceptor {}".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile second = new MockMultipartFile(
                "file", "UserInfoInterceptor.java", "text/x-java-source",
                "public class UserInfoInterceptor {}".getBytes(StandardCharsets.UTF_8)
        );

        DocumentResponse firstResponse = documentService.upload(first, "STANDARD_SOURCE");
        DocumentResponse secondResponse = documentService.upload(second, "STANDARD_SOURCE");

        assertThat(secondResponse.id()).isEqualTo(firstResponse.id());
        assertThat(repository.findAll().stream()
                .filter(document -> firstResponse.id().equals(document.getId()))
                .count()).isEqualTo(1);
    }

    @Test
    void sameContentIsStoredSeparatelyForDifferentProjects() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String firstKey = "project-a-" + suffix;
        String secondKey = "project-b-" + suffix;
        projectService.create(new KnowledgeProjectRequest(firstKey, "Project A", null));
        projectService.create(new KnowledgeProjectRequest(secondKey, "Project B", null));

        byte[] content = "public class SharedType {}".getBytes(StandardCharsets.UTF_8);
        DocumentResponse first = documentService.upload(firstKey,
                new MockMultipartFile("file", "SharedType.java", "text/x-java-source", content),
                "STANDARD_SOURCE");
        DocumentResponse second = documentService.upload(secondKey,
                new MockMultipartFile("file", "SharedType.java", "text/x-java-source", content),
                "STANDARD_SOURCE");

        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(first.projectKey()).isEqualTo(firstKey);
        assertThat(second.projectKey()).isEqualTo(secondKey);
        assertThat(repository.findPage(firstKey, 10, 0)).extracting(RagDocument::getId).contains(first.id());
        assertThat(repository.findPage(secondKey, 10, 0)).extracting(RagDocument::getId).contains(second.id());
    }
    @Test
    void repositoryFindsStoredDocumentByGraphSourceKey() throws IOException {
        Path javaFile = STORAGE_DIRECTORY.resolve("RoleController.java");
        Files.writeString(javaFile, "package com.example;\npublic class RoleController {}", StandardCharsets.UTF_8);

        RagDocument document = RagDocument.create(
                "RoleController.java",
                "stored-RoleController.java",
                javaFile.toString(),
                Files.size(javaFile),
                "text/x-java-source",
                DocumentType.STANDARD_SOURCE
        );
        RagDocument savedDocument = repository.save(document);
        String sourceKey = RagDocument.sourceKey(savedDocument.getId());

        repository.updateIndexMetadata(savedDocument.getId(), "abc123", sourceKey, sourceKey);

        RagDocument foundDocument = repository.findByGraphSourceKey(sourceKey);

        assertThat(foundDocument).isNotNull();
        assertThat(foundDocument.getId()).isEqualTo(savedDocument.getId());
        assertThat(foundDocument.getGraphSourceKey()).isEqualTo(sourceKey);
        assertThat(foundDocument.getFilePath()).isEqualTo(javaFile.toString());
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(DocumentServiceTest::deleteIfExists);
        }
    }

    private static void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete test file: " + path, exception);
        }
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        RecordingDocumentIndexWorkflow recordingDocumentIndexWorkflow(RagDocumentRepository repository) {
            return new RecordingDocumentIndexWorkflow(repository);
        }
    }

    static class RecordingDocumentIndexWorkflow extends DocumentIndexWorkflow {
        private final AtomicReference<RagDocument> lastDocument = new AtomicReference<>();

        RecordingDocumentIndexWorkflow(RagDocumentRepository repository) {
            super(List.of(), repository);
        }

        @Override
        public void run(RagDocument document) {
            lastDocument.set(document);
        }

        RagDocument lastDocument() {
            return lastDocument.get();
        }

        void reset() {
            lastDocument.set(null);
        }
    }
}
