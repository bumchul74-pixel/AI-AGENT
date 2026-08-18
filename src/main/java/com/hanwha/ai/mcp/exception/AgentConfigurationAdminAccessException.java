package com.hanwha.ai.mcp.exception;

import org.springframework.http.HttpStatus;

public class AgentConfigurationAdminAccessException extends RuntimeException {
    private final HttpStatus status;

    public AgentConfigurationAdminAccessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
