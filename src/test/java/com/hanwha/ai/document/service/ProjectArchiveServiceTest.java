package com.hanwha.ai.document.service;

import com.hanwha.ai.global.exception.BusinessException;
import com.hanwha.ai.document.dto.DocumentResponse;
import com.hanwha.ai.document.dto.ProjectArchiveUploadResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectArchiveServiceTest {

    private DocumentService documentService;
    private ProjectArchiveService projectArchiveService;

    @BeforeEach
    void setUp() {
        documentService = mock(DocumentService.class);
        projectArchiveService = new ProjectArchiveService(documentService);

        AtomicLong sequence = new AtomicLong();
        when(documentService.uploadExtracted(eq("default"), anyString(), any(byte[].class), eq("STANDARD_SOURCE")))
                .thenAnswer(invocation -> response(
                        sequence.incrementAndGet(),
                        invocation.getArgument(1, String.class)
                ));
    }

    @Test
    void extractsAndIndexesSupportedProjectFiles() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("sample/src/main/java/com/example/UserService.java", "class UserService {}".getBytes());
        entries.put("sample/src/main/resources/mapper/UserMapper.xml", "<mapper namespace='UserMapper'/>".getBytes());
        entries.put("sample/target/UserService.class", new byte[]{1, 2, 3});

        MockMultipartFile archive = archive("sample-project.zip", entries);

        ProjectArchiveUploadResponse result = projectArchiveService.upload(archive);

        assertThat(result.archiveName()).isEqualTo("sample-project.zip");
        assertThat(result.discoveredFiles()).isEqualTo(3);
        assertThat(result.indexedFiles()).isEqualTo(2);
        assertThat(result.skippedFiles()).isEqualTo(1);
        assertThat(result.failedFiles()).isZero();
        assertThat(result.documents())
                .extracting(DocumentResponse::originalFileName)
                .containsExactly(
                        "sample/src/main/java/com/example/UserService.java",
                        "sample/src/main/resources/mapper/UserMapper.xml"
                );

        verify(documentService).uploadExtracted(
                eq("default"),
                eq("sample/src/main/java/com/example/UserService.java"),
                any(byte[].class),
                eq("STANDARD_SOURCE")
        );
        verify(documentService).uploadExtracted(
                eq("default"),
                eq("sample/src/main/resources/mapper/UserMapper.xml"),
                any(byte[].class),
                eq("STANDARD_SOURCE")
        );
    }

    @Test
    void rejectsZipSlipEntry() throws Exception {
        MockMultipartFile archive = archive(
                "unsafe.zip",
                Map.of("../outside.java", "class Outside {}".getBytes())
        );

        assertThatThrownBy(() -> projectArchiveService.upload(archive))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unsafe ZIP entry path");
    }

    private MockMultipartFile archive(String name, Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return new MockMultipartFile("file", name, "application/zip", output.toByteArray());
    }

    private DocumentResponse response(long id, String originalFileName) {
        LocalDateTime now = LocalDateTime.now();
        return new DocumentResponse(
                id,
                originalFileName,
                1L,
                "application/octet-stream",
                "STANDARD_SOURCE",
                "hash-" + id,
                "document:" + id,
                "document:" + id,
                "INDEXED",
                "INDEXED",
                "INDEXED",
                1,
                null,
                now,
                now
        );
    }
}
