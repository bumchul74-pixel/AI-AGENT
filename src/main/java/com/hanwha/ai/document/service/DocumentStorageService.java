package com.hanwha.ai.document.service;

import com.hanwha.ai.document.config.DocumentProperties;
import com.hanwha.ai.document.domain.RagDocument;
import com.hanwha.ai.global.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentStorageService {
    private final Path rootDirectory;

    public DocumentStorageService(DocumentProperties properties) {
        this.rootDirectory = Path.of(properties.storageDirectory()).toAbsolutePath().normalize();
    }

    public StoredDocumentFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Upload file is required.");
        }

        String originalFileName = StringUtils.cleanPath(
                Objects.requireNonNullElse(file.getOriginalFilename(), "")
        );
        if (!StringUtils.hasText(originalFileName)) {
            throw new BusinessException("Original file name is required.");
        }
        if (originalFileName.contains("..")) {
            throw new BusinessException("Invalid file name: " + originalFileName);
        }

        try {
            Files.createDirectories(rootDirectory);
            String storedFileName = UUID.randomUUID() + extension(originalFileName);
            Path target = rootDirectory.resolve(storedFileName).normalize();
            if (!target.startsWith(rootDirectory)) {
                throw new BusinessException("Invalid file storage path.");
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }

            return new StoredDocumentFile(
                    originalFileName,
                    storedFileName,
                    target.toString(),
                    file.getSize(),
                    contentType(file)
            );
        } catch (IOException exception) {
            throw new BusinessException("Failed to store upload file.", exception);
        }
    }

    public Resource load(RagDocument document) {
        try {
            Path path = resolveManagedPath(document);
            if (!Files.exists(path)) {
                throw new BusinessException("Stored file does not exist.");
            }
            return new UrlResource(path.toUri());
        } catch (IOException exception) {
            throw new BusinessException("Failed to load stored file.", exception);
        }
    }

    public void delete(RagDocument document) {
        try {
            Files.deleteIfExists(resolveManagedPath(document));
        } catch (IOException exception) {
            throw new BusinessException("Failed to delete stored file.", exception);
        }
    }

    /** Removes a staged file when an upload is a duplicate of an active row. */
    public void discard(StoredDocumentFile storedFile) {
        try {
            Files.deleteIfExists(resolveManagedPath(Path.of(storedFile.filePath())));
        } catch (IOException exception) {
            throw new BusinessException("Failed to discard duplicate upload.", exception);
        }
    }

    private Path resolveManagedPath(RagDocument document) {
        return resolveManagedPath(Path.of(document.getFilePath()));
    }

    private Path resolveManagedPath(Path candidate) {
        Path path = candidate.toAbsolutePath().normalize();
        if (!path.startsWith(rootDirectory)) {
            throw new BusinessException("Stored file is outside the document storage directory.");
        }
        return path;
    }

    private String extension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0) {
            return "";
        }
        return fileName.substring(dotIndex).toLowerCase(Locale.ROOT);
    }

    private String contentType(MultipartFile file) {
        if (StringUtils.hasText(file.getContentType())) {
            return file.getContentType();
        }
        return "application/octet-stream";
    }
}
