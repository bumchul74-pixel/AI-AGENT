package com.hanwha.ai.mcp.router;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerFilesystemMcpRouterTest {
    @TempDir
    Path tempDir;

    @Test
    void routesServerFilesystemMcpAndFindsJavaFile() throws IOException {
        Path sourceDirectory = tempDir.resolve(Path.of("src", "main", "java", "com", "hanwha", "ai"));
        Files.createDirectories(sourceDirectory);
        Path javaFile = sourceDirectory.resolve("AiAgentApplication.java");
        Files.writeString(javaFile, "package com.hanwha.ai; public class AiAgentApplication {}");

        ServerFilesystemMcpRouter router = new ServerFilesystemMcpRouter(tempDir);


        List<Path> findFile = router.findJavaFiles("AiAgentApplication.java");
        findFile.forEach(path -> {
            System.out.println("found path = " + path);
            System.out.println("file name = " + path.getFileName());
        });        
        assertThat(router.serverName()).isEqualTo("server-filesystem");
        assertThat(router.rootPath()).isEqualTo(tempDir);
        assertThat(router.findJavaFiles("AiAgentApplication.java"))
                .containsExactly(javaFile);
    }

    @Test
    void ignoresNonJavaFilesWithSameNamePattern() throws IOException {
        Files.writeString(tempDir.resolve("AiAgentApplication.txt"), "not java");

        ServerFilesystemMcpRouter router = new ServerFilesystemMcpRouter(tempDir);

        assertThat(router.findJavaFiles("AiAgentApplication.txt"))
                .isEmpty();
    }
}
