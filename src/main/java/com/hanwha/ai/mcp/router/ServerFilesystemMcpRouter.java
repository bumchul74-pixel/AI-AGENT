package com.hanwha.ai.mcp.router;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ServerFilesystemMcpRouter implements McpRouter {
    public static final String SERVER_NAME = "server-filesystem";

    private static final String ROOT_PROPERTY =
            "mcp.filesystem.root";
    private static final int SEARCH_MAX_DEPTH = 20;

    private final Path rootPath;

    @Autowired
    public ServerFilesystemMcpRouter(Environment environment) {
        this(Path.of(environment.getRequiredProperty(ROOT_PROPERTY)));
    }

    ServerFilesystemMcpRouter(Path rootPath) {
        this.rootPath = rootPath;
    }

    @Override
    public String serverName() {
        return SERVER_NAME;
    }

    public Path rootPath() {
        return rootPath;
    }

    public List<Path> findJavaFiles(String fileName) throws IOException {
        try (var paths = Files.find(
                rootPath,
                SEARCH_MAX_DEPTH,
                (path, attributes) -> attributes.isRegularFile()
                        && path.getFileName().toString().equals(fileName)
                        && path.toString().endsWith(".java")
        )) {
            return paths.toList();
        }
    }
}
