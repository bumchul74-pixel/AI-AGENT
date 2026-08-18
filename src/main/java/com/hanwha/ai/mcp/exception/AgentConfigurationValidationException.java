package com.hanwha.ai.mcp.exception;

import com.hanwha.ai.global.exception.BusinessException;

public class AgentConfigurationValidationException extends BusinessException {
    public AgentConfigurationValidationException(String message) {
        super(message);
    }

    public AgentConfigurationValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
