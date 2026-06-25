package com.hanwha.ai.generation.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "generation")
public record GenerationProperties(List<ProjectStructure> projectStructures) {
    @Override
    public List<ProjectStructure> projectStructures() {
        return projectStructures == null ? List.of() : projectStructures;
    }

    public record ProjectStructure(
            String name,
            String value
    ) {
    }
}
