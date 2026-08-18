package com.hanwha.ai.mcp.orchestration;

import com.hanwha.ai.generation.service.ProjectStructureAnalyzer;
import com.hanwha.ai.mcp.domain.AgentExecutionHistory;
import com.hanwha.ai.mcp.gateway.AiMcpGatewayService;
import com.hanwha.ai.mcp.repository.AgentExecutionHistoryRepository;
import com.hanwha.ai.mcp.router.AgentCapability;
import com.hanwha.ai.mcp.router.AgentRegistry;
import com.hanwha.ai.mcp.router.AgentRoute;
import com.hanwha.ai.mcp.router.AgentRegistrySnapshot;
import com.hanwha.ai.mcp.router.AgentRouter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "spring.ai.mcp.client", name = "enabled", havingValue = "true")
public class AgentOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);
    private static final String SAFE_EXECUTION_ERROR = "Agent execution failed.";

    private final AgentRouter agentRouter;
    private final AgentRegistry agentRegistry;
    private final AgentPlanner agentPlanner;
    private final AiMcpGatewayService aiMcpGatewayService;
    private final ProjectStructureAnalyzer projectStructureAnalyzer;
    private final AgentExecutionHistoryRepository historyRepository;
    private final Integer maxParallelismOverride;

    @Autowired
    public AgentOrchestrator(
            AgentRouter agentRouter,
            AgentRegistry agentRegistry,
            AgentPlanner agentPlanner,
            AiMcpGatewayService aiMcpGatewayService,
            ProjectStructureAnalyzer projectStructureAnalyzer,
            AgentExecutionHistoryRepository historyRepository
    ) {
        this(agentRouter, agentRegistry, agentPlanner, aiMcpGatewayService,
                projectStructureAnalyzer, historyRepository, null);
    }

    public AgentOrchestrator(
            AgentRouter agentRouter,
            AgentRegistry agentRegistry,
            AiMcpGatewayService aiMcpGatewayService,
            ProjectStructureAnalyzer projectStructureAnalyzer,
            AgentExecutionHistoryRepository historyRepository
    ) {
        this(agentRouter, agentRegistry, new AgentPlanner(agentRegistry, agentRouter),
                aiMcpGatewayService, projectStructureAnalyzer, historyRepository, 4);
    }

    AgentOrchestrator(
            AgentRouter agentRouter,
            AgentRegistry agentRegistry,
            AgentPlanner agentPlanner,
            AiMcpGatewayService aiMcpGatewayService,
            ProjectStructureAnalyzer projectStructureAnalyzer,
            AgentExecutionHistoryRepository historyRepository,
            Integer maxParallelism
    ) {
        this.agentRouter = agentRouter;
        this.agentRegistry = agentRegistry;
        this.agentPlanner = agentPlanner;
        this.aiMcpGatewayService = aiMcpGatewayService;
        this.projectStructureAnalyzer = projectStructureAnalyzer;
        this.historyRepository = historyRepository;
        this.maxParallelismOverride = maxParallelism == null ? null : Math.max(1, maxParallelism);
    }

    public boolean supports(String message) {
        return agentRouter.supports(message);
    }

    public AgentExecutionResult execute(String message) {
        AgentPlan plan = agentPlanner.plan(message);
        AgentPlanStep firstStep = plan.steps().getFirst();
        String executionId = UUID.randomUUID().toString();
        LocalDateTime startedAt = LocalDateTime.now();
        long startedNanos = System.nanoTime();
        boolean historyStarted = recordStarted(
                executionId, message, plan.configurationVersion(),
                firstStep.route(), firstStep.capability(), startedAt
        );

        List<StepOutcome> outcomes = executePlan(plan, message);
        List<AgentStepExecution> steps = outcomes.stream().map(StepOutcome::execution).toList();
        List<StepOutcome> succeeded = outcomes.stream()
                .filter(outcome -> outcome.execution().successful())
                .toList();

        if (succeeded.isEmpty()) {
            RuntimeException failure = outcomes.stream()
                    .map(StepOutcome::failure)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElseGet(() -> new IllegalStateException("Agent plan dependencies failed."));
            recordFailed(historyStarted, executionId, startedNanos, failure);
            throw failure;
        }

        recordSucceeded(historyStarted, executionId, startedNanos);
        return new AgentExecutionResult(executionId, overallStatus(steps), steps);
    }

    private List<StepOutcome> executePlan(AgentPlan plan, String message) {
        Map<String, CompletableFuture<StepOutcome>> futures = new ConcurrentHashMap<>();
        int parallelism = maxParallelismOverride == null ? plan.maxParallelism() : maxParallelismOverride;
        try (ExecutorService executor = Executors.newFixedThreadPool(parallelism)) {
            for (AgentPlanStep step : plan.steps()) {
                CompletableFuture<?>[] dependencies = step.dependencies().stream()
                        .map(futures::get)
                        .toArray(CompletableFuture[]::new);
                CompletableFuture<StepOutcome> future = CompletableFuture.allOf(dependencies)
                        .thenApplyAsync(ignored -> {
                            boolean dependencyFailed = step.dependencies().stream()
                                    .map(futures::get)
                                    .map(CompletableFuture::join)
                                    .anyMatch(outcome -> !outcome.execution().successful());
                            return dependencyFailed ? skipped(step) : executeStep(step, message, plan.snapshot());
                        }, executor);
                futures.put(step.id(), future);
            }
            return plan.steps().stream().map(step -> futures.get(step.id()).join()).toList();
        }
    }

    private StepOutcome executeStep(
            AgentPlanStep step,
            String message,
            AgentRegistrySnapshot planSnapshot
    ) {
        long startedNanos = System.nanoTime();
        AgentCapability capability = step.capability();
        if (capability == null) {
            return executeCandidate(step, null, step.route(), 1, null, startedNanos);
        }

        AttemptResult primary = attempt(capability, step.route());
        if (primary.failure() == null) {
            return succeeded(step, capability, step.route(), primary, null, startedNanos);
        }

        int attempts = primary.attempts();
        RuntimeException lastFailure = primary.failure();
        AgentRegistrySnapshot snapshot = planSnapshot == null
                ? agentRegistry.snapshot()
                : planSnapshot;
        for (String fallbackId : capability.fallbackCapabilityIds()) {
            AgentCapability fallback = snapshot.requiredCapability(fallbackId);
            AgentRoute fallbackRoute = agentRouter.routeCapability(fallbackId, message, snapshot);
            AttemptResult fallbackResult = attempt(fallback, fallbackRoute);
            attempts += fallbackResult.attempts();
            if (fallbackResult.failure() == null) {
                return succeeded(
                        step, capability, fallbackRoute,
                        new AttemptResult(fallbackResult.result(), attempts, null),
                        fallbackId, startedNanos
                );
            }
            lastFailure = fallbackResult.failure();
        }
        return failed(step, capability, attempts, lastFailure, startedNanos);
    }

    private StepOutcome executeCandidate(
            AgentPlanStep step,
            AgentCapability capability,
            AgentRoute route,
            int attempts,
            String fallbackId,
            long startedNanos
    ) {
        try {
            Object result = executeRoute(route);
            AttemptResult attempt = new AttemptResult(result, attempts, null);
            return succeeded(step, capability, route, attempt, fallbackId, startedNanos);
        } catch (RuntimeException exception) {
            return failed(step, capability, attempts, exception, startedNanos);
        }
    }

    private AttemptResult attempt(AgentCapability capability, AgentRoute route) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= capability.maxAttempts(); attempt++) {
            try {
                return new AttemptResult(executeRoute(route), attempt, null);
            } catch (RuntimeException exception) {
                lastFailure = exception;
                if (attempt < capability.maxAttempts()) {
                    waitBeforeRetry(capability);
                }
            }
        }
        return new AttemptResult(null, capability.maxAttempts(), lastFailure);
    }

    private void waitBeforeRetry(AgentCapability capability) {
        try {
            Thread.sleep(Math.max(0, capability.retryBackoff().toMillis()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Agent retry interrupted.", exception);
        }
    }

    private StepOutcome succeeded(
            AgentPlanStep step,
            AgentCapability capability,
            AgentRoute route,
            AttemptResult attempt,
            String fallbackId,
            long startedNanos
    ) {
        AgentStepExecution.Status status = fallbackId == null
                ? AgentStepExecution.Status.SUCCEEDED
                : AgentStepExecution.Status.FALLBACK_SUCCEEDED;
        return new StepOutcome(new AgentStepExecution(
                step.id(),
                capability == null ? null : capability.agentId(),
                capability == null ? null : capability.id(),
                step.dependencies(),
                route,
                status,
                attempt.attempts(),
                fallbackId,
                elapsedMillis(startedNanos),
                attempt.result(),
                null
        ), null);
    }

    private StepOutcome failed(
            AgentPlanStep step,
            AgentCapability capability,
            int attempts,
            RuntimeException failure,
            long startedNanos
    ) {
        return new StepOutcome(new AgentStepExecution(
                step.id(),
                capability == null ? null : capability.agentId(),
                capability == null ? null : capability.id(),
                step.dependencies(),
                step.route(),
                AgentStepExecution.Status.FAILED,
                attempts,
                null,
                elapsedMillis(startedNanos),
                null,
                failure.getClass().getSimpleName()
        ), failure);
    }

    private StepOutcome skipped(AgentPlanStep step) {
        AgentCapability capability = step.capability();
        return new StepOutcome(new AgentStepExecution(
                step.id(),
                capability == null ? null : capability.agentId(),
                capability == null ? null : capability.id(),
                step.dependencies(),
                step.route(),
                AgentStepExecution.Status.SKIPPED,
                0,
                null,
                0,
                null,
                "DependencyFailed"
        ), null);
    }

    private AgentExecutionResult.Status overallStatus(List<AgentStepExecution> steps) {
        boolean failed = steps.stream().anyMatch(step -> !step.successful());
        boolean fallback = steps.stream()
                .anyMatch(step -> step.status() == AgentStepExecution.Status.FALLBACK_SUCCEEDED);
        if (failed) {
            return AgentExecutionResult.Status.PARTIAL;
        }
        return fallback
                ? AgentExecutionResult.Status.SUCCEEDED_WITH_FALLBACK
                : AgentExecutionResult.Status.SUCCEEDED;
    }

    private Object executeRoute(AgentRoute route) {
        return switch (route.kind()) {
            case TOOL_CALL -> aiMcpGatewayService.callTool(route.target(), route.arguments());
            case PROJECT_STRUCTURE_ANALYSIS -> projectStructureAnalyzer.analyze(route.target(), List.of());
            case RESOURCE_READ -> aiMcpGatewayService.readResource(route.target());
            case RESOURCE_LIST -> aiMcpGatewayService.listResources();
            case PROMPT_GET -> aiMcpGatewayService.getPrompt(route.target(), route.arguments());
            case PROMPT_LIST -> aiMcpGatewayService.listPrompts();
            case TOOL_LIST -> aiMcpGatewayService.listTools();
            case PING -> aiMcpGatewayService.ping();
            case SERVER_INFO -> aiMcpGatewayService.serverInfo();
        };
    }

    private boolean recordStarted(
            String executionId,
            String message,
            String configurationVersion,
            AgentRoute route,
            AgentCapability capability,
            LocalDateTime startedAt
    ) {
        AgentExecutionHistory history = new AgentExecutionHistory(
                executionId,
                capability == null ? null : capability.agentId(),
                capability == null ? null : capability.id(),
                configurationVersion,
                route.kind().name(),
                capability == null ? route.kind().name() : capability.tool(),
                requestHash(message),
                "STARTED",
                null,
                null,
                null,
                startedAt,
                null
        );
        try {
            historyRepository.recordStarted(history);
            return true;
        } catch (RuntimeException exception) {
            log.warn(
                    "Agent execution history start failed. executionId={}, errorType={}",
                    executionId,
                    exception.getClass().getSimpleName()
            );
            return false;
        }
    }

    private void recordSucceeded(boolean historyStarted, String executionId, long startedNanos) {
        if (!historyStarted) {
            return;
        }
        try {
            historyRepository.recordSucceeded(executionId, LocalDateTime.now(), elapsedMillis(startedNanos));
        } catch (RuntimeException exception) {
            log.warn(
                    "Agent execution history completion failed. executionId={}, errorType={}",
                    executionId,
                    exception.getClass().getSimpleName()
            );
        }
    }

    private void recordFailed(
            boolean historyStarted,
            String executionId,
            long startedNanos,
            Throwable exception
    ) {
        if (!historyStarted) {
            return;
        }
        try {
            historyRepository.recordFailed(
                    executionId,
                    LocalDateTime.now(),
                    elapsedMillis(startedNanos),
                    exception.getClass().getSimpleName(),
                    SAFE_EXECUTION_ERROR
            );
        } catch (RuntimeException historyException) {
            log.warn(
                    "Agent execution history failure update failed. executionId={}, errorType={}",
                    executionId,
                    historyException.getClass().getSimpleName()
            );
        }
    }

    private long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private String requestHash(String message) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((message == null ? "" : message).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record AttemptResult(Object result, int attempts, RuntimeException failure) {
    }

    private record StepOutcome(AgentStepExecution execution, RuntimeException failure) {
    }
}
