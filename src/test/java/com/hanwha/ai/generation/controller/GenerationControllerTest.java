package com.hanwha.ai.generation.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanwha.ai.generation.config.GenerationProperties;
import com.hanwha.ai.generation.dto.GenerationResponse;
import com.hanwha.ai.generation.service.GenerationService;
import java.util.List;
import org.junit.jupiter.api.Test;

class GenerationControllerTest {
    @Test
    void returnsProjectStructureOptionsFromConfiguration() {
        GenerationController controller = new GenerationController(
                request -> new GenerationResponse(String.join(", ", request.targetTypes()), request.targetTypes(), "", List.of(), request.projectStructure()),
                new GenerationProperties(List.of(
                        new GenerationProperties.ProjectStructure("Layered", "Base package: com.example"),
                        new GenerationProperties.ProjectStructure("MyBatis", "mapper paths")
                ))
        );

        assertThat(controller.projectStructures())
                .extracting("name", "value")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Layered", "Base package: com.example"),
                        org.assertj.core.groups.Tuple.tuple("MyBatis", "mapper paths")
                );
    }
}
