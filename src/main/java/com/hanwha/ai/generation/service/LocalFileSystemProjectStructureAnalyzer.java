package com.hanwha.ai.generation.service;

import com.hanwha.ai.global.exception.BusinessException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(prefix = "spring.ai.mcp.client", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LocalFileSystemProjectStructureAnalyzer implements ProjectStructureAnalyzer {
    private static final int MAX_DEPTH = 12;
    private static final int MAX_ENTRIES = 100;
    private static final Set<String> IGNORED_PATH_PARTS = Set.of(
            ".git",
            ".gradle",
            ".idea",
            ".vscode",
            "build",
            "dist",
            "node_modules",
            "out",
            "target"
    );

    @Override
    public String analyze(String projectPath, List<String> targetTypes) {
        if (!StringUtils.hasText(projectPath)) {
            throw new BusinessException("projectStructure must contain a project full path.");
        }

        Path root = toPath(projectPath);
        String targetTypesText = targetTypes == null || targetTypes.isEmpty()
                ? "none"
                : String.join(", ", targetTypes);

        if (!Files.exists(root)) {
            return formatAnalysis(root, targetTypesText, "not found", List.of());
        }

        if (!Files.isDirectory(root)) {
            return formatAnalysis(root, targetTypesText, "not a directory", List.of());
        }

        try (Stream<Path> paths = Files.walk(root, MAX_DEPTH)) {
            List<String> entries = paths
                    .filter(path -> !path.equals(root))
                    .filter(path -> !isIgnored(root, path))
                    .sorted(Comparator.comparing(path -> root.relativize(path).toString().toLowerCase(Locale.ROOT)))
                    .limit(MAX_ENTRIES)
                    .map(path -> formatEntry(root, path))
                    .collect(Collectors.toList());

            return formatAnalysis(root, targetTypesText, "readable", entries);
        } catch (IOException | SecurityException exception) {
            return formatAnalysis(root, targetTypesText, "not readable: " + exception.getMessage(), List.of());
        }
    }

    private Path toPath(String projectPath) {
        try {
            return Path.of(projectPath.trim()).normalize();
        } catch (InvalidPathException exception) {
            throw new BusinessException("projectStructure must contain a valid project full path.", exception);
        }
    }

    private boolean isIgnored(Path root, Path path) {
        Path relativePath = root.relativize(path);
        for (Path part : relativePath) {
            if (IGNORED_PATH_PARTS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private String formatEntry(Path root, Path path) {
        String prefix = Files.isDirectory(path) ? "[dir] " : "[file] ";
        return prefix + root.relativize(path);
    }

    private String formatAnalysis(Path root, String targetTypesText, String status, List<String> entries) {
        String structure = entries.isEmpty() ? "No project files were discovered." : String.join("\n", entries);
        return """
                Local project structure analysis:
                Tool: LocalFileSystemProjectStructureAnalyzer
                Project full path: %s
                Project path status: %s
                Selected target types: %s

                Analysis result:
                %s
                """.formatted(root, status, targetTypesText, structure);
    }
}
