package com.hanwha.ai.document.domain;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class DocumentFileSupport {
    private static final Set<String> TEST_SOURCE_DIRECTORIES = Set.of(
            "test",
            "tests",
            "testcase",
            "testcases"
    );
    private static final Pattern TEST_JAVA_FILE_NAME = Pattern.compile(
            "^(?:Test.*|.*Test|.*Tests|.*TestCase|IT.*|.*IT|.*ITCase)\\.java$"
    );
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

    public static boolean isTestJavaSourceFile(String fileName) {
        if (!isJavaSourceFile(fileName)) {
            return false;
        }
        String normalizedPath = fileName == null ? "" : fileName.trim().replace('\\', '/');
        String[] segments = normalizedPath.split("/");
        for (int index = 0; index < segments.length - 1; index++) {
            if (TEST_SOURCE_DIRECTORIES.contains(segments[index].toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        String baseName = segments.length == 0 ? normalizedPath : segments[segments.length - 1];
        return TEST_JAVA_FILE_NAME.matcher(baseName).matches();
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
