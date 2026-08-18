package com.hanwha.ai.mcp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hanwha.ai.mcp.repository.AgentConfigurationRepository;
import com.hanwha.ai.mcp.router.AgentCapability;
import com.hanwha.ai.mcp.router.AgentRegistry;
import com.hanwha.ai.mcp.router.DefaultAgentArgumentResolver;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class AgentConfigurationServiceTest {
    @Test
    void storesAndActivatesBeforePublishingTheNewMemorySnapshot() {
        AgentOrchestrationProperties properties = new AgentOrchestrationProperties();
        InMemoryRepository repository = new InMemoryRepository();
        AgentRegistry registry = AgentRegistry.of(List.of(capability("old", "old_tool")));
        PlatformTransactionManager transactionManager = transactionManager();
        AgentConfigurationDocument document = document("new", "new_tool", 2);
        AgentConfigurationService service = service(
                properties,
                repository,
                registry,
                transactionManager,
                document
        );

        AgentConfigurationView result = service.saveAndActivate(document, "tester");

        verify(transactionManager).commit(any());
        assertThat(repository.active).isNotNull();
        assertThat(repository.active.versionKey()).isEqualTo(result.version());
        assertThat(registry.version()).isEqualTo(result.version());
        assertThat(registry.maxParallelism()).isEqualTo(2);
        assertThat(registry.findByTool("old_tool")).isEmpty();
        assertThat(registry.findByTool("new_tool")).isPresent();
    }

    @Test
    void commitFailureDoesNotPublishTheCandidateSnapshot() {
        AgentOrchestrationProperties properties = new AgentOrchestrationProperties();
        InMemoryRepository repository = new InMemoryRepository();
        AgentRegistry registry = AgentRegistry.of("known-good", 1, List.of(
                capability("known", "known_tool")
        ));
        PlatformTransactionManager transactionManager = transactionManager();
        doThrow(new IllegalStateException("commit failed"))
                .when(transactionManager).commit(any());
        AgentConfigurationService service = service(
                properties,
                repository,
                registry,
                transactionManager,
                document("candidate", "candidate_tool", 2)
        );

        assertThatThrownBy(() -> service.saveAndActivate(
                document("candidate", "candidate_tool", 2),
                "tester"
        )).isInstanceOf(IllegalStateException.class);

        assertThat(registry.version()).isEqualTo("known-good");
        assertThat(registry.findByTool("known_tool")).isPresent();
        assertThat(registry.findByTool("candidate_tool")).isEmpty();
    }
    @Test
    void invalidEditorValuesDoNotCreateAVersionOrReplaceMemory() {
        AgentOrchestrationProperties properties = new AgentOrchestrationProperties();
        InMemoryRepository repository = new InMemoryRepository();
        AgentRegistry registry = AgentRegistry.of("known-good", 1, List.of(
                capability("known", "known_tool")
        ));
        AgentConfigurationService service = service(
                properties,
                repository,
                registry,
                transactionManager(),
                document("bootstrap", "bootstrap_tool", 1)
        );

        assertThatThrownBy(() -> service.saveAndActivate(
                document("invalid", "invalid_tool", 0),
                "admin-ui"
        )).hasMessageContaining("maxParallelism");

        assertThat(repository.versions).isEmpty();
        assertThat(registry.version()).isEqualTo("known-good");
        assertThat(registry.findByTool("known_tool")).isPresent();
    }
    @Test
    void pollingFailureKeepsTheLastKnownGoodSnapshot() {
        AgentOrchestrationProperties properties = new AgentOrchestrationProperties();
        InMemoryRepository repository = new InMemoryRepository();
        repository.active = new AgentConfigurationVersion(
                1L,
                "broken-version",
                "ACTIVE",
                2,
                "{invalid-json",
                "external",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        AgentRegistry registry = AgentRegistry.of("known-good", 1, List.of(
                capability("known", "known_tool")
        ));
        AgentConfigurationService service = service(
                properties,
                repository,
                registry,
                transactionManager(),
                document("bootstrap", "bootstrap_tool", 1)
        );

        service.refreshOnSchedule();

        assertThat(registry.version()).isEqualTo("known-good");
        assertThat(registry.findByTool("known_tool")).isPresent();
    }

    private AgentConfigurationService service(
            AgentOrchestrationProperties properties,
            AgentConfigurationRepository repository,
            AgentRegistry registry,
            PlatformTransactionManager transactionManager,
            AgentConfigurationDocument bootstrap
    ) {
        AgentConfigurationBootstrapLoader bootstrapLoader =
                mock(AgentConfigurationBootstrapLoader.class);
        when(bootstrapLoader.load()).thenReturn(bootstrap);
        return new AgentConfigurationService(
                properties,
                bootstrapLoader,
                new AgentConfigurationCodec(),
                new AgentConfigurationValidator(List.of(new DefaultAgentArgumentResolver())),
                repository,
                registry,
                new TransactionTemplate(transactionManager)
        );
    }

    private PlatformTransactionManager transactionManager() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        return transactionManager;
    }

    private AgentConfigurationDocument document(
            String capabilityId,
            String tool,
            int maxParallelism
    ) {
        return new AgentConfigurationDocument(maxParallelism, List.of(
                new AgentConfigurationDocument.AgentDefinition(
                        "test-agent",
                        "Test Agent",
                        true,
                        "mcp",
                        "ai-mcp",
                        List.of(new AgentConfigurationDocument.CapabilityDefinition(
                                capabilityId,
                                tool,
                                true,
                                List.of(capabilityId),
                                "none",
                                10,
                                30_000,
                                false,
                                List.of(),
                                1,
                                100,
                                List.of()
                        ))
                )
        ));
    }

    private AgentCapability capability(String id, String tool) {
        return new AgentCapability(
                "test-agent",
                id,
                "mcp",
                "ai-mcp",
                tool,
                Set.of(id),
                "none",
                10,
                java.time.Duration.ofSeconds(30),
                false
        );
    }

    private static final class InMemoryRepository implements AgentConfigurationRepository {
        private final List<AgentConfigurationVersion> versions = new ArrayList<>();
        private AgentConfigurationVersion active;

        @Override
        public Optional<AgentConfigurationVersion> findActive() {
            return Optional.ofNullable(active);
        }

        @Override
        public Optional<AgentConfigurationVersion> findByVersionKey(String versionKey) {
            return versions.stream()
                    .filter(version -> version.versionKey().equals(versionKey))
                    .findFirst()
                    .or(() -> Optional.ofNullable(active)
                            .filter(version -> version.versionKey().equals(versionKey)));
        }

        @Override
        public void insert(AgentConfigurationVersion version) {
            versions.add(version);
        }

        @Override
        public void archiveActive() {
            active = null;
        }

        @Override
        public void activate(String versionKey, LocalDateTime activatedAt) {
            AgentConfigurationVersion draft = versions.stream()
                    .filter(version -> version.versionKey().equals(versionKey))
                    .findFirst()
                    .orElseThrow();
            active = new AgentConfigurationVersion(
                    draft.id(),
                    draft.versionKey(),
                    "ACTIVE",
                    draft.maxParallelism(),
                    draft.configurationJson(),
                    draft.createdBy(),
                    draft.createdAt(),
                    activatedAt
            );
        }
    }
}