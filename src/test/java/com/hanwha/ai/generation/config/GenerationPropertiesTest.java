package com.hanwha.ai.generation.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.ai.mcp.client.enabled=false")
class GenerationPropertiesTest {
    @Autowired
    private GenerationProperties generationProperties;

    @Test
    void bindsProjectStructureOptionsFromApplicationYaml() {
        assertThat(generationProperties.projectStructures())
                .isNotEmpty()
                .allSatisfy(projectStructure -> {
                    assertThat(projectStructure.name()).isNotBlank();
                    assertThat(projectStructure.value()).isNotBlank();
                });
    }
}