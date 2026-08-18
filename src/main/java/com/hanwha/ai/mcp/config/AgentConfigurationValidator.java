package com.hanwha.ai.mcp.config;

import com.hanwha.ai.mcp.exception.AgentConfigurationValidationException;
import com.hanwha.ai.mcp.router.AgentArgumentResolver;
import com.hanwha.ai.mcp.router.AgentCapability;
import com.hanwha.ai.mcp.router.AgentRegistrySnapshot;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AgentConfigurationValidator {
    private final List<AgentArgumentResolver> argumentResolvers;

    public AgentConfigurationValidator(List<AgentArgumentResolver> argumentResolvers) {
        this.argumentResolvers = List.copyOf(argumentResolvers);
    }

    public AgentRegistrySnapshot validate(
            String version,
            AgentConfigurationDocument document
    ) {
        if (document == null) {
            throw new AgentConfigurationValidationException("Agent configuration is required.");
        }
        AgentRegistrySnapshot snapshot;
        try {
            snapshot = AgentRegistrySnapshot.from(version, document);
        } catch (IllegalStateException exception) {
            throw new AgentConfigurationValidationException(exception.getMessage(), exception);
        }
        for (AgentCapability capability : snapshot.capabilities()) {
            if (!"mcp".equals(capability.executor()) || !"ai-mcp".equals(capability.server())) {
                throw new AgentConfigurationValidationException(
                        "Unsupported capability execution target: " + capability.id()
                );
            }
            if (argumentResolvers.stream()
                    .noneMatch(resolver -> resolver.supports(capability.argumentResolver()))) {
                throw new AgentConfigurationValidationException(
                        "No AgentArgumentResolver for capability " + capability.id()
                                + ": " + capability.argumentResolver()
                );
            }
        }
        return snapshot;
    }
}