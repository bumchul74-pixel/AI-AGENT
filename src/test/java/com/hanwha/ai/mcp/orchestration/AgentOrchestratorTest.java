package com.hanwha.ai.mcp.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hanwha.ai.generation.service.ProjectStructureAnalyzer;
import com.hanwha.ai.mcp.domain.AgentExecutionHistory;
import com.hanwha.ai.mcp.gateway.AiMcpGatewayService;
import com.hanwha.ai.mcp.repository.AgentExecutionHistoryRepository;
import com.hanwha.ai.mcp.router.AgentRegistry;
import com.hanwha.ai.mcp.router.AgentRouter;
import com.hanwha.ai.mcp.router.AgentRouterTestFixture;
import com.hanwha.ai.mcp.router.DefaultAgentArgumentResolver;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AgentOrchestratorTest {
    @Test
    void executesExactlyOneRouteAndRecordsSuccessfulCapability() {
        AtomicInteger calls = new AtomicInteger();
        AiMcpGatewayService gateway = gateway((name, arguments) -> {
            calls.incrementAndGet();
            return new McpSchema.CallToolResult(List.of(), false, null, Map.of());
        });
        RecordingHistoryRepository history = new RecordingHistoryRepository();
        AgentOrchestrator orchestrator = orchestrator(gateway, (path, types) -> "", history);

        AgentExecutionResult result = orchestrator.execute("list_rules를 실행해줘");

        assertThat(calls).hasValue(1);
        assertThat(result.route().target()).isEqualTo("list_rules");
        assertThat(history.started.agentId()).isEqualTo("test-agent");
        assertThat(history.started.capabilityId()).isEqualTo("security.rule-list");
        assertThat(history.started.target()).isEqualTo("list_rules");
        assertThat(history.started.requestHash()).hasSize(64).doesNotContain("list_rules");
        assertThat(history.succeededId).isEqualTo(result.executionId());
        assertThat(history.failedId).isNull();
    }

    @Test
    void recordsSafeFailureMetadataAndRethrowsExecutionFailure() {
        AiMcpGatewayService gateway = gateway((name, arguments) -> {
            throw new IllegalStateException("sensitive tool output");
        });
        RecordingHistoryRepository history = new RecordingHistoryRepository();
        AgentOrchestrator orchestrator = orchestrator(gateway, (path, types) -> "", history);

        assertThatThrownBy(() -> orchestrator.execute("list_rules를 실행해줘"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("sensitive tool output");

        assertThat(history.failedId).isEqualTo(history.started.executionId());
        assertThat(history.errorType).isEqualTo("IllegalStateException");
        assertThat(history.errorMessage).isEqualTo("Agent execution failed.");
        assertThat(history.errorMessage).doesNotContain("sensitive tool output");
    }

    @Test
    void historyPersistenceFailureDoesNotBlockAgentExecution() {
        AtomicInteger calls = new AtomicInteger();
        AiMcpGatewayService gateway = gateway((name, arguments) -> {
            calls.incrementAndGet();
            return new McpSchema.CallToolResult(List.of(), false, null, Map.of());
        });
        RecordingHistoryRepository history = new RecordingHistoryRepository();
        history.failOnStart = true;
        AgentOrchestrator orchestrator = orchestrator(gateway, (path, types) -> "", history);

        AgentExecutionResult result = orchestrator.execute("list_rules를 실행해줘");

        assertThat(result.route().target()).isEqualTo("list_rules");
        assertThat(calls).hasValue(1);
        assertThat(history.succeededId).isNull();
    }

    @Test
    void doesNotPersistLocalProjectPathAsHistoryTarget() {
        RecordingHistoryRepository history = new RecordingHistoryRepository();
        AgentOrchestrator orchestrator = orchestrator(
                new AiMcpGatewayService(null),
                (path, types) -> "analyzed",
                history
        );

        orchestrator.execute("D:\\workspace\\secret-project 프로젝트 구조를 분석해줘");

        assertThat(history.started.routeKind()).isEqualTo("PROJECT_STRUCTURE_ANALYSIS");
        assertThat(history.started.target()).isEqualTo("PROJECT_STRUCTURE_ANALYSIS");
        assertThat(history.started.target()).doesNotContain("secret-project");
    }

    private AgentOrchestrator orchestrator(
            AiMcpGatewayService gateway,
            ProjectStructureAnalyzer analyzer,
            AgentExecutionHistoryRepository historyRepository
    ) {
        AgentRegistry registry = AgentRouterTestFixture.registry();
        AgentRouter router = new AgentRouter(registry, List.of(new DefaultAgentArgumentResolver()));
        return new AgentOrchestrator(router, registry, gateway, analyzer, historyRepository);
    }

    private AiMcpGatewayService gateway(ToolCall toolCall) {
        return new AiMcpGatewayService(null) {
            @Override
            public McpSchema.CallToolResult callTool(String name, Map<String, Object> arguments) {
                return toolCall.call(name, arguments);
            }
        };
    }

    @FunctionalInterface
    private interface ToolCall {
        McpSchema.CallToolResult call(String name, Map<String, Object> arguments);
    }

    private static final class RecordingHistoryRepository implements AgentExecutionHistoryRepository {
        private AgentExecutionHistory started;
        private String succeededId;
        private String failedId;
        private String errorType;
        private String errorMessage;
        private boolean failOnStart;

        @Override
        public void recordStarted(AgentExecutionHistory history) {
            if (failOnStart) {
                throw new IllegalStateException("history database unavailable");
            }
            started = history;
        }

        @Override
        public void recordSucceeded(String executionId, LocalDateTime completedAt, long durationMs) {
            succeededId = executionId;
        }

        @Override
        public void recordFailed(
                String executionId,
                LocalDateTime completedAt,
                long durationMs,
                String failureType,
                String failureMessage
        ) {
            failedId = executionId;
            errorType = failureType;
            errorMessage = failureMessage;
        }
    }
}
