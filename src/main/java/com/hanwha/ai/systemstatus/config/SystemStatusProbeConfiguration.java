package com.hanwha.ai.systemstatus.config;

import com.hanwha.ai.llm.config.GeminiProperties;
import com.hanwha.ai.llm.config.LlmProperties;
import com.hanwha.ai.llm.config.OpenAiProperties;
import com.hanwha.ai.rag.config.RagProperties;
import com.hanwha.ai.sourcegraph.config.SourceGraphProperties;
import com.hanwha.ai.systemstatus.domain.SystemDependencyDescriptor;
import com.hanwha.ai.systemstatus.domain.SystemProbeOutcome;
import com.hanwha.ai.systemstatus.service.DefaultSystemDependencyProbe;
import com.hanwha.ai.systemstatus.service.SystemDependencyProbe;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.StringUtils;

@Configuration
@EnableScheduling
public class SystemStatusProbeConfiguration {
    @Bean
    public SystemDependencyProbe applicationStatusProbe() {
        return probe(
                descriptor("backend", "Spring Boot Backend", "CORE", true, "LIVENESS"),
                () -> SystemProbeOutcome.up("애플리케이션이 요청을 처리하고 있습니다.")
        );
    }

    @Bean
    public SystemDependencyProbe databaseStatusProbe(
            DataSource dataSource,
            SystemStatusProperties properties
    ) {
        return probe(
                descriptor("postgresql", "PostgreSQL", "STORAGE", true, "READINESS"),
                () -> databaseOutcome(dataSource, properties.connectTimeout())
        );
    }

    @Bean
    public SystemDependencyProbe ragStatusProbe(
            RagProperties ragProperties,
            SystemStatusProperties properties
    ) {
        return probe(
                descriptor("rag", "Python RAG", "SEARCH", true, "HTTP_HEALTH"),
                () -> httpHealthOutcome(ragProperties.baseUrl(), "/health", properties.connectTimeout())
        );
    }

    @Bean
    public SystemDependencyProbe aiMcpStatusProbe(
            Environment environment,
            SystemStatusProperties properties
    ) {
        return probe(
                descriptor("ai-mcp", "AI-MCP", "MCP", false, "CONNECTIVITY"),
                () -> optionalTcpOutcome(
                        environment.getProperty("spring.ai.mcp.client.enabled", Boolean.class, true),
                        environment.getProperty(
                                "spring.ai.mcp.client.streamable-http.connections.ai-mcp.url",
                                "http://localhost:8092"
                        ),
                        properties.connectTimeout(),
                        "MCP client가 비활성화되어 있습니다."
                )
        );
    }

    @Bean
    public SystemDependencyProbe easyOcrStatusProbe(
            Environment environment,
            SystemStatusProperties properties
    ) {
        return probe(
                descriptor("easyocr", "EasyOCR MCP", "MCP", false, "CONNECTIVITY"),
                () -> optionalTcpOutcome(
                        environment.getProperty("spring.ai.mcp.client.enabled", Boolean.class, true),
                        environment.getProperty(
                                "spring.ai.mcp.client.streamable-http.connections.easyocr.url",
                                "http://localhost:8001"
                        ),
                        properties.connectTimeout(),
                        "MCP client가 비활성화되어 있습니다."
                )
        );
    }

    @Bean
    public SystemDependencyProbe neo4jStatusProbe(
            Environment environment,
            SourceGraphProperties sourceGraphProperties,
            SystemStatusProperties properties
    ) {
        return probe(
                descriptor("neo4j", "Neo4j", "GRAPH", false, "CONNECTIVITY"),
                () -> optionalTcpOutcome(
                        sourceGraphProperties.enabled(),
                        environment.getProperty("spring.neo4j.uri", "bolt://localhost:7687"),
                        properties.connectTimeout(),
                        "Source Graph가 비활성화되어 있습니다."
                )
        );
    }

    @Bean
    public SystemDependencyProbe llmStatusProbe(
            LlmProperties llmProperties,
            OpenAiProperties openAiProperties,
            GeminiProperties geminiProperties
    ) {
        return probe(
                descriptor("llm", "LLM Provider", "AI", true, "CONFIGURATION"),
                () -> llmOutcome(llmProperties, openAiProperties, geminiProperties)
        );
    }

    private SystemDependencyProbe probe(
            SystemDependencyDescriptor descriptor,
            Supplier<SystemProbeOutcome> checker
    ) {
        return new DefaultSystemDependencyProbe(descriptor, checker);
    }

    private SystemDependencyDescriptor descriptor(
            String id,
            String name,
            String category,
            boolean critical,
            String checkType
    ) {
        return new SystemDependencyDescriptor(id, name, category, critical, checkType);
    }

    private SystemProbeOutcome databaseOutcome(DataSource dataSource, Duration timeout) {
        try (Connection connection = dataSource.getConnection()) {
            int timeoutSeconds = Math.max(1, Math.toIntExact(timeout.toSeconds()));
            return connection.isValid(timeoutSeconds)
                    ? SystemProbeOutcome.up("데이터베이스 연결이 정상입니다.")
                    : SystemProbeOutcome.down("데이터베이스 연결 검증에 실패했습니다.");
        } catch (SQLException exception) {
            return SystemProbeOutcome.down("데이터베이스에 연결할 수 없습니다.");
        }
    }

    private SystemProbeOutcome httpHealthOutcome(
            String baseUrl,
            String path,
            Duration timeout
    ) {
        if (!StringUtils.hasText(baseUrl)) {
            return SystemProbeOutcome.down("Health endpoint가 설정되지 않았습니다.");
        }
        try {
            URI uri = URI.create(stripTrailingSlash(baseUrl) + path);
            HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .GET()
                    .build();
            HttpResponse<Void> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.discarding()
            );
            return response.statusCode() >= 200 && response.statusCode() < 300
                    ? SystemProbeOutcome.up("Health endpoint가 정상 응답했습니다.")
                    : SystemProbeOutcome.down("Health endpoint가 비정상 응답했습니다.");
        } catch (IllegalArgumentException | IOException exception) {
            return SystemProbeOutcome.down("Health endpoint에 연결할 수 없습니다.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return SystemProbeOutcome.down("Health endpoint 점검이 중단되었습니다.");
        }
    }

    private SystemProbeOutcome optionalTcpOutcome(
            boolean enabled,
            String endpoint,
            Duration timeout,
            String disabledMessage
    ) {
        if (!enabled) {
            return SystemProbeOutcome.unknown(disabledMessage);
        }
        if (!StringUtils.hasText(endpoint)) {
            return SystemProbeOutcome.down("연결 endpoint가 설정되지 않았습니다.");
        }
        try {
            URI uri = URI.create(endpoint);
            int port = uri.getPort() > 0 ? uri.getPort() : defaultPort(uri.getScheme());
            if (!StringUtils.hasText(uri.getHost()) || port <= 0) {
                return SystemProbeOutcome.down("연결 endpoint 형식이 올바르지 않습니다.");
            }
            try (Socket socket = new Socket()) {
                socket.connect(
                        new InetSocketAddress(uri.getHost(), port),
                        Math.toIntExact(timeout.toMillis())
                );
            }
            return SystemProbeOutcome.up("서비스 endpoint에 연결할 수 있습니다.");
        } catch (IllegalArgumentException | IOException exception) {
            return SystemProbeOutcome.down("서비스 endpoint에 연결할 수 없습니다.");
        }
    }

    private SystemProbeOutcome llmOutcome(
            LlmProperties llmProperties,
            OpenAiProperties openAiProperties,
            GeminiProperties geminiProperties
    ) {
        String provider = String.valueOf(llmProperties.provider()).toLowerCase(Locale.ROOT);
        boolean configured = switch (provider) {
            case "openai" -> hasLlmConfiguration(
                    openAiProperties.apiKey(), openAiProperties.model(), openAiProperties.baseUrl());
            case "gemini" -> hasLlmConfiguration(
                    geminiProperties.apiKey(), geminiProperties.model(), geminiProperties.baseUrl());
            default -> false;
        };
        return configured
                ? SystemProbeOutcome.up(provider + " 설정이 준비되어 있습니다. 실제 호출은 수행하지 않았습니다.")
                : SystemProbeOutcome.down("선택된 LLM Provider 설정이 불완전합니다.");
    }

    private boolean hasLlmConfiguration(String apiKey, String model, String baseUrl) {
        return StringUtils.hasText(apiKey)
                && StringUtils.hasText(model)
                && StringUtils.hasText(baseUrl);
    }

    private String stripTrailingSlash(String value) {
        return value.replaceFirst("/+$", "");
    }

    private int defaultPort(String scheme) {
        if ("https".equalsIgnoreCase(scheme)) return 443;
        if ("http".equalsIgnoreCase(scheme)) return 80;
        if ("bolt".equalsIgnoreCase(scheme)) return 7687;
        return -1;
    }
}
