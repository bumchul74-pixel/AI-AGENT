package com.hanwha.ai.mcp.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanwha.ai.generation.service.ProjectStructureAnalyzer;
import com.hanwha.ai.mcp.domain.AgentExecutionHistory;
import com.hanwha.ai.mcp.gateway.AiMcpGatewayService;
import com.hanwha.ai.mcp.repository.AgentExecutionHistoryRepository;
import com.hanwha.ai.mcp.router.AgentCapability;
import com.hanwha.ai.mcp.router.AgentRegistry;
import com.hanwha.ai.mcp.router.AgentRouter;
import com.hanwha.ai.mcp.router.DefaultAgentArgumentResolver;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AgentWorkflowOrchestrationTest {
    @Test
    void plansDependenciesBeforeTheRequestedCapability() {
        AgentCapability dependency = capability("metadata", "metadata_tool", List.of(), 1, List.of());
        AgentCapability root = capability("generate", "generate_tool", List.of("metadata"), 1, List.of());
        AgentRegistry registry = AgentRegistry.of(List.of(root, dependency));
        AgentRouter router = router(registry);

        AgentPlan plan = new AgentPlanner(registry, router).plan("generate_tool 실행");

        assertThat(plan.steps()).extracting(AgentPlanStep::id)
                .containsExactly("metadata", "generate");
        assertThat(plan.steps().get(1).dependencies()).containsExactly("metadata");
    }

    @Test
    void retriesPrimaryThenUsesConfiguredFallback() {
        AtomicInteger primaryCalls = new AtomicInteger();
        AtomicInteger fallbackCalls = new AtomicInteger();
        AgentCapability fallback = capability("fallback", "fallback_tool", List.of(), 1, List.of());
        AgentCapability primary = capability("primary", "primary_tool", List.of(), 2, List.of("fallback"));
        AgentRegistry registry = AgentRegistry.of(List.of(primary, fallback));
        AgentOrchestrator orchestrator = orchestrator(registry, (name, arguments) -> {
            if ("primary_tool".equals(name)) {
                primaryCalls.incrementAndGet();
                throw new IllegalStateException("primary unavailable");
            }
            fallbackCalls.incrementAndGet();
            return success();
        }, 2);

        AgentExecutionResult result = orchestrator.execute("primary_tool 실행");

        assertThat(primaryCalls).hasValue(2);
        assertThat(fallbackCalls).hasValue(1);
        assertThat(result.status()).isEqualTo(AgentExecutionResult.Status.SUCCEEDED_WITH_FALLBACK);
        assertThat(result.steps()).singleElement().satisfies(step -> {
            assertThat(step.status()).isEqualTo(AgentStepExecution.Status.FALLBACK_SUCCEEDED);
            assertThat(step.attempts()).isEqualTo(3);
            assertThat(step.fallbackCapabilityId()).isEqualTo("fallback");
        });
    }

    @Test
    void runsIndependentMentionedCapabilitiesInParallel() {
        CountDownLatch started = new CountDownLatch(2);
        AgentRegistry registry = AgentRegistry.of(List.of(
                capability("first", "first_tool", List.of(), 1, List.of()),
                capability("second", "second_tool", List.of(), 1, List.of())
        ));
        AgentOrchestrator orchestrator = orchestrator(registry, (name, arguments) -> {
            started.countDown();
            try {
                if (!started.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("steps did not overlap");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return success();
        }, 2);

        AgentExecutionResult result = orchestrator.execute("first_tool second_tool 실행");

        assertThat(result.steps()).hasSize(2).allMatch(AgentStepExecution::successful);
        assertThat(result.status()).isEqualTo(AgentExecutionResult.Status.SUCCEEDED);
    }

    @Test
    void keepsTheConfigurationSnapshotCapturedAtPlanCreation() {
        AgentCapability original = capability("original", "original_tool", List.of(), 1, List.of());
        AgentRegistry registry = AgentRegistry.of("version-one", 2, List.of(original));
        AgentRouter router = router(registry);
        AgentPlan plan = new AgentPlanner(registry, router).plan("original_tool 실행");

        registry.publish(com.hanwha.ai.mcp.router.AgentRegistrySnapshot.of(
                "version-two",
                1,
                List.of(capability("replacement", "replacement_tool", List.of(), 1, List.of()))
        ));

        assertThat(plan.configurationVersion()).isEqualTo("version-one");
        assertThat(plan.maxParallelism()).isEqualTo(2);
        assertThat(plan.snapshot().findByTool("original_tool")).isPresent();
        assertThat(plan.snapshot().findByTool("replacement_tool")).isEmpty();
    }
    private AgentOrchestrator orchestrator(AgentRegistry registry, ToolCall toolCall, int parallelism) {
        AgentRouter router = router(registry);
        AiMcpGatewayService gateway = new AiMcpGatewayService(null) {
            @Override
            public McpSchema.CallToolResult callTool(String name, Map<String, Object> arguments) {
                return toolCall.call(name, arguments);
            }
        };
        ProjectStructureAnalyzer analyzer = (path, types) -> "";
        return new AgentOrchestrator(
                router,
                registry,
                new AgentPlanner(registry, router),
                gateway,
                analyzer,
                new NoOpHistoryRepository(),
                parallelism
        );
    }

    private AgentRouter router(AgentRegistry registry) {
        return new AgentRouter(registry, List.of(new DefaultAgentArgumentResolver()));
    }

    private AgentCapability capability(
            String id,
            String tool,
            List<String> dependencies,
            int maxAttempts,
            List<String> fallbacks
    ) {
        return new AgentCapability(
                "test-agent",
                id,
                "mcp",
                "ai-mcp",
                tool,
                Set.of(id),
                "none",
                1,
                Duration.ofSeconds(5),
                false,
                dependencies,
                maxAttempts,
                Duration.ZERO,
                fallbacks
        );
    }

    private McpSchema.CallToolResult success() {
        return new McpSchema.CallToolResult(List.of(), false, null, Map.of());
    }

    @FunctionalInterface
    private interface ToolCall {
        McpSchema.CallToolResult call(String name, Map<String, Object> arguments);
    }

    private static final class NoOpHistoryRepository implements AgentExecutionHistoryRepository {
        @Override
        public void recordStarted(AgentExecutionHistory history) {
        }

        @Override
        public void recordSucceeded(String executionId, LocalDateTime completedAt, long durationMs) {
        }

        @Override
        public void recordFailed(
                String executionId,
                LocalDateTime completedAt,
                long durationMs,
                String failureType,
                String failureMessage
        ) {
        }
    }
}