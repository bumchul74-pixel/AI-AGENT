package com.hanwha.ai.mcp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hanwha.ai.mcp.config.AgentConfigurationAdminAccessGuard;
import com.hanwha.ai.mcp.config.AgentConfigurationDocument;
import com.hanwha.ai.mcp.config.AgentConfigurationService;
import com.hanwha.ai.mcp.config.AgentConfigurationView;
import com.hanwha.ai.mcp.config.AgentOrchestrationProperties;
import com.hanwha.ai.mcp.dto.AgentConfigurationUpdateRequest;
import com.hanwha.ai.mcp.exception.AgentConfigurationAdminAccessException;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentConfigurationAdminControllerTest {
    @Test
    void checksTheFeatureGateBeforeReadingSavingAndRefreshing() {
        AgentOrchestrationProperties properties = new AgentOrchestrationProperties();
        properties.setAdminApiEnabled(true);
        AgentConfigurationAdminAccessGuard accessGuard =
                new AgentConfigurationAdminAccessGuard(properties);
        AgentConfigurationService service = mock(AgentConfigurationService.class);
        AgentConfigurationDocument document = new AgentConfigurationDocument(2, List.of());
        AgentConfigurationView active = new AgentConfigurationView("version-1", "DATABASE", document);
        AgentConfigurationView saved = new AgentConfigurationView("version-2", "DATABASE", document);
        when(service.active()).thenReturn(active);
        when(service.saveAndActivate(document, "admin-ui")).thenReturn(saved);
        when(service.refresh()).thenReturn(saved);
        AgentConfigurationAdminController controller =
                new AgentConfigurationAdminController(accessGuard, service);

        assertThat(controller.active()).isEqualTo(active);
        assertThat(controller.saveAndActivate(
                new AgentConfigurationUpdateRequest(document)
        )).isEqualTo(saved);
        assertThat(controller.refresh()).isEqualTo(saved);

        verify(service).active();
        verify(service).saveAndActivate(document, "admin-ui");
        verify(service).refresh();
    }

    @Test
    void doesNotCallTheServiceWhenTheFeatureIsDisabled() {
        AgentOrchestrationProperties properties = new AgentOrchestrationProperties();
        properties.setAdminApiEnabled(false);
        AgentConfigurationService service = mock(AgentConfigurationService.class);
        AgentConfigurationAdminController controller = new AgentConfigurationAdminController(
                new AgentConfigurationAdminAccessGuard(properties),
                service
        );

        assertThatThrownBy(controller::active)
                .isInstanceOf(AgentConfigurationAdminAccessException.class);
        verifyNoInteractions(service);
    }
}
