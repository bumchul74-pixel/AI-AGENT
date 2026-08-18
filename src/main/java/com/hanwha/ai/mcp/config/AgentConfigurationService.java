package com.hanwha.ai.mcp.config;

import com.hanwha.ai.mcp.repository.AgentConfigurationRepository;
import com.hanwha.ai.mcp.router.AgentRegistry;
import com.hanwha.ai.mcp.router.AgentRegistrySnapshot;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
public class AgentConfigurationService implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AgentConfigurationService.class);

    private final AgentOrchestrationProperties properties;
    private final AgentConfigurationBootstrapLoader bootstrapLoader;
    private final AgentConfigurationCodec codec;
    private final AgentConfigurationValidator validator;
    private final AgentConfigurationRepository repository;
    private final AgentRegistry registry;
    private final TransactionTemplate transactionTemplate;

    public AgentConfigurationService(
            AgentOrchestrationProperties properties,
            AgentConfigurationBootstrapLoader bootstrapLoader,
            AgentConfigurationCodec codec,
            AgentConfigurationValidator validator,
            AgentConfigurationRepository repository,
            AgentRegistry registry,
            TransactionTemplate transactionTemplate
    ) {
        this.properties = properties;
        this.bootstrapLoader = bootstrapLoader;
        this.codec = codec;
        this.validator = validator;
        this.repository = repository;
        this.registry = registry;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isDatabaseEnabled()) {
            log.info("Agent configuration database is disabled. version=bootstrap");
            return;
        }
        try {
            AgentConfigurationVersion active = repository.findActive().orElse(null);
            if (active == null && properties.isSeedEnabled()) {
                AgentConfigurationView seeded = saveAndActivate(
                        bootstrapLoader.load(),
                        "bootstrap-seed"
                );
                log.info("Agent configuration seed activated. version={}", seeded.version());
                return;
            }
            if (active == null) {
                if (!properties.isFallbackOnStartup()) {
                    throw new IllegalStateException("No active Agent configuration exists.");
                }
                log.warn("No active Agent configuration. Keeping bootstrap snapshot.");
                return;
            }
            publish(active);
        } catch (RuntimeException exception) {
            if (!properties.isFallbackOnStartup()) {
                throw exception;
            }
            log.warn(
                    "Agent configuration startup load failed. Keeping bootstrap snapshot. errorType={}",
                    exception.getClass().getSimpleName()
            );
        }
    }

    public AgentConfigurationView saveAndActivate(
            AgentConfigurationDocument document,
            String changedBy
    ) {
        if (!properties.isDatabaseEnabled()) {
            throw new IllegalStateException("Agent configuration database is disabled.");
        }
        String version = UUID.randomUUID().toString();
        AgentRegistrySnapshot candidate = validator.validate(version, document);
        String json = codec.write(document);
        LocalDateTime now = LocalDateTime.now();
        AgentConfigurationVersion stored = new AgentConfigurationVersion(
                null,
                version,
                "DRAFT",
                document.maxParallelism(),
                json,
                normalizeChangedBy(changedBy),
                now,
                null
        );

        transactionTemplate.executeWithoutResult(status -> {
            repository.insert(stored);
            repository.archiveActive();
            repository.activate(version, now);
        });

        registry.publish(candidate);
        log.info("Agent configuration activated. version={}", version);
        return new AgentConfigurationView(version, "DATABASE", document);
    }

    public AgentConfigurationView active() {
        AgentRegistrySnapshot snapshot = registry.snapshot();
        if (AgentConfigurationBootstrapLoader.BOOTSTRAP_VERSION.equals(snapshot.version())) {
            return new AgentConfigurationView(
                    snapshot.version(),
                    "BOOTSTRAP",
                    bootstrapLoader.load()
            );
        }
        AgentConfigurationVersion active = repository.findByVersionKey(snapshot.version())
                .orElseThrow(() -> new IllegalStateException(
                        "Active in-memory Agent configuration version is not in the database."
                ));
        return new AgentConfigurationView(
                active.versionKey(),
                "DATABASE",
                codec.read(active.configurationJson())
        );
    }

    public AgentConfigurationView refresh() {
        if (!properties.isDatabaseEnabled()) {
            return active();
        }
        AgentConfigurationVersion active = repository.findActive()
                .orElseThrow(() -> new IllegalStateException(
                        "No active Agent configuration exists."
                ));
        if (!active.versionKey().equals(registry.version())) {
            publish(active);
        }
        return active();
    }

    @Scheduled(fixedDelayString = "${agent.orchestration.config.refresh-interval-ms:30000}")
    public void refreshOnSchedule() {
        if (!properties.isDatabaseEnabled()
                || properties.getRefreshMode() != AgentOrchestrationProperties.RefreshMode.POLLING) {
            return;
        }
        try {
            refresh();
        } catch (RuntimeException exception) {
            log.warn(
                    "Agent configuration refresh failed. Keeping last-known-good snapshot. "
                            + "version={}, errorType={}",
                    registry.version(),
                    exception.getClass().getSimpleName()
            );
        }
    }

    private void publish(AgentConfigurationVersion active) {
        AgentConfigurationDocument document = codec.read(active.configurationJson());
        if (document.maxParallelism() != active.maxParallelism()) {
            throw new IllegalStateException(
                    "Agent configuration maxParallelism does not match stored metadata."
            );
        }
        registry.publish(validator.validate(active.versionKey(), document));
        log.info("Agent configuration snapshot published. version={}", active.versionKey());
    }

    private String normalizeChangedBy(String changedBy) {
        return StringUtils.hasText(changedBy) ? changedBy.trim() : "system";
    }
}