package com.hanwha.ai.mcp.config;

import com.hanwha.ai.mcp.exception.AgentConfigurationAdminAccessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class AgentConfigurationAdminAccessGuard {
    private final AgentOrchestrationProperties properties;

    public AgentConfigurationAdminAccessGuard(AgentOrchestrationProperties properties) {
        this.properties = properties;
    }

    public void requireEnabled() {
        if (!properties.isAdminApiEnabled()) {
            throw new AgentConfigurationAdminAccessException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Agent configuration management API is disabled."
            );
        }
        HttpServletRequest request = currentRequest();
        if (request != null && !isLoopback(request.getRemoteAddr())) {
            throw new AgentConfigurationAdminAccessException(
                    HttpStatus.FORBIDDEN,
                    "Agent configuration management API is available only from localhost."
            );
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private boolean isLoopback(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return false;
        }
        return "0:0:0:0:0:0:0:1".equals(remoteAddress)
                || "::1".equals(remoteAddress)
                || remoteAddress.startsWith("127.")
                || remoteAddress.startsWith("::ffff:127.");
    }
}
