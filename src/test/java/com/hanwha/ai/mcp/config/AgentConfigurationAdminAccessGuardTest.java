package com.hanwha.ai.mcp.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hanwha.ai.mcp.exception.AgentConfigurationAdminAccessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AgentConfigurationAdminAccessGuardTest {
    @Test
    void failsClosedWhenTheAdminApiIsDisabled() {
        AgentOrchestrationProperties properties = new AgentOrchestrationProperties();
        properties.setAdminApiEnabled(false);
        AgentConfigurationAdminAccessGuard accessGuard =
                new AgentConfigurationAdminAccessGuard(properties);

        assertThatThrownBy(accessGuard::requireEnabled)
                .isInstanceOfSatisfying(
                        AgentConfigurationAdminAccessException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.status())
                                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                );
    }

    @Test
    void allowsLocalRequestsWhenTheAdminApiIsEnabledByDefault() {
        AgentOrchestrationProperties properties = new AgentOrchestrationProperties();
        AgentConfigurationAdminAccessGuard accessGuard =
                new AgentConfigurationAdminAccessGuard(properties);

        assertThatCode(accessGuard::requireEnabled).doesNotThrowAnyException();
    }

    @Test
    void rejectsRemoteRequestsEvenWhenEnabledByDefault() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.10.1.25");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        AgentConfigurationAdminAccessGuard accessGuard =
                new AgentConfigurationAdminAccessGuard(new AgentOrchestrationProperties());

        try {
            assertThatThrownBy(accessGuard::requireEnabled)
                    .isInstanceOfSatisfying(
                            AgentConfigurationAdminAccessException.class,
                            exception -> org.assertj.core.api.Assertions.assertThat(
                                    exception.status()
                            ).isEqualTo(HttpStatus.FORBIDDEN)
                    );
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
