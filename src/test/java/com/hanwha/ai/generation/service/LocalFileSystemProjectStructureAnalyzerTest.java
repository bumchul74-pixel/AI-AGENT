package com.hanwha.ai.generation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileSystemProjectStructureAnalyzerTest {
    @TempDir
    private Path tempDir;

    @Test
    void analyzesLocalProjectStructureWithoutMcp() throws IOException {
        Path sourceDirectory = Files.createDirectories(tempDir.resolve(Path.of(
                "src",
                "main",
                "java",
                "com",
                "hanwha",
                "ai",
                "user",
                "controller"
        )));
        Files.writeString(sourceDirectory.resolve("UserController.java"), "class UserController {}");

        LocalFileSystemProjectStructureAnalyzer analyzer = new LocalFileSystemProjectStructureAnalyzer();
        String analysis = analyzer.analyze(tempDir.toString(), List.of("Controller"));

        assertThat(analysis)
                .contains("Local project structure analysis")
                .contains("Project path status: readable")
                .contains("Selected target types: Controller")
                .contains("UserController.java");
    }
}
