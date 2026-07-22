package com.hanwha.ai.document.service;

import com.hanwha.ai.document.domain.DocumentFileSupport;
import com.hanwha.ai.document.dto.DocumentResponse;
import com.hanwha.ai.document.dto.ProjectArchiveUploadResponse;
import com.hanwha.ai.document.dto.ProjectArchiveUploadResponse.ArchiveFailure;
import com.hanwha.ai.global.exception.BusinessException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProjectArchiveService {
    private static final int MAX_ENTRIES = 5000;
    private static final long MAX_ENTRY_BYTES = 10L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 200L * 1024 * 1024;
    private final DocumentService documentService;

    public ProjectArchiveService(DocumentService documentService) {
        this.documentService = documentService;
    }

    public ProjectArchiveUploadResponse upload(MultipartFile archive) {
        return upload("default", archive);
    }

    public ProjectArchiveUploadResponse upload(String projectKey, MultipartFile archive) {
        String archiveName = archive == null ? "" : archive.getOriginalFilename();
        if (archive == null || archive.isEmpty() || archiveName == null
                || !archiveName.toLowerCase().endsWith(".zip")) {
            throw new BusinessException("A non-empty ZIP project archive is required.");
        }
        List<DocumentResponse> documents = new ArrayList<>();
        List<ArchiveFailure> failures = new ArrayList<>();
        int discovered = 0;
        int skipped = 0;
        long totalBytes = 0;
        try (InputStream input = archive.getInputStream(); ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (++discovered > MAX_ENTRIES) throw new BusinessException("ZIP contains too many files.");
                String entryPath = safeEntryPath(entry.getName());
                if (!DocumentFileSupport.isSupportedVectorFile(entryPath)) {
                    skipped++;
                    continue;
                }
                byte[] content = readEntry(zip, entryPath);
                totalBytes += content.length;
                if (totalBytes > MAX_TOTAL_BYTES) throw new BusinessException("ZIP extracted content exceeds 200 MB.");
                try {
                    documents.add(documentService.uploadExtracted(projectKey, entryPath, content, "STANDARD_SOURCE"));
                } catch (Exception exception) {
                    failures.add(new ArchiveFailure(entryPath, rootMessage(exception)));
                }
            }
        } catch (IOException exception) {
            throw new BusinessException("Failed to read ZIP project archive.", exception);
        }
        return new ProjectArchiveUploadResponse(archiveName, discovered, documents.size(), skipped,
                failures.size(), List.copyOf(documents), List.copyOf(failures));
    }

    private String safeEntryPath(String name) {
        String normalized = name == null ? "" : name.replace('\\', '/');
        Path path = Path.of(normalized).normalize();
        if (normalized.isBlank() || path.isAbsolute() || normalized.startsWith("/")
                || path.startsWith("..") || normalized.contains(":")) {
            throw new BusinessException("Unsafe ZIP entry path: " + name);
        }
        return path.toString().replace('\\', '/');
    }

    private byte[] readEntry(ZipInputStream zip, String entryPath) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = zip.read(buffer)) != -1) {
            if ((long) output.size() + read > MAX_ENTRY_BYTES) {
                throw new BusinessException("ZIP entry exceeds 10 MB: " + entryPath);
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
