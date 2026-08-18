package com.hanwha.ai.mcp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hanwha.ai.mcp.dto.AgentToolCatalogResponse;
import com.hanwha.ai.mcp.service.AgentToolCatalogService;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentToolCatalogControllerTest {
    @Test
    void returnsTheActiveCatalogWithoutCallingTheMcpGateway() {
        AgentToolCatalogService service = mock(AgentToolCatalogService.class);
        AgentToolCatalogResponse expected = new AgentToolCatalogResponse(
                "database-version-7",
                List.of()
        );
        when(service.activeTools()).thenReturn(expected);

        AgentToolCatalogResponse actual =
                new AgentToolCatalogController(service).activeTools();

        assertThat(actual).isSameAs(expected);
    }
}