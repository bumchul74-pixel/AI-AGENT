package com.hanwha.ai.document.domain;

import java.util.Locale;
import java.util.Set;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class DocumentFileSupport {
    private static final Set<String> SUPPORTED_VECTOR_EXTENSIONS = Set.of(
            ".java",
            ".kt",
            ".xml",
            ".sql",
            ".yml",
            ".yaml",
            ".md",
            ".js",
            ".jsx",
            ".ts",
            ".tsx"
    );
    private static final Set<String> TEMPORARY_SUFFIXES = Set.of(
            ".tmp",
            ".part",
            ".crdownload"
    );

    private DocumentFileSupport() {
    }

    public static boolean isSupportedVectorFile(String fileName) {
        return !isTemporaryFile(fileName) && SUPPORTED_VECTOR_EXTENSIONS.contains(extension(fileName));
    }

    public static boolean isJavaSourceFile(String fileName) {
        return ".java".equals(extension(fileName));
    }

    public static boolean isGraphSourceFile(String fileName) {
        return Set.of(".java", ".xml", ".yml", ".yaml", ".md").contains(extension(fileName));
    }

    public static boolean isTemporaryFile(String fileName) {
        String normalized = normalize(fileName);
        return TEMPORARY_SUFFIXES.stream().anyMatch(normalized::endsWith);
    }

    public static String supportedExtensionsDescription() {
        return String.join(", ", SUPPORTED_VECTOR_EXTENSIONS);
    }

    /** Returns a stable content identity for an uploaded file. */
    public static String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Failed to calculate document file hash.", exception);
        }
    }
    private static String extension(String fileName) {
        String normalized = normalize(fileName);
        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex < 0) {
            return "";
        }
        return normalized.substring(dotIndex);
    }

    private static String normalize(String fileName) {
        if (fileName == null) {
            return "";
        }
        return fileName.trim().toLowerCase(Locale.ROOT);
    }
}
