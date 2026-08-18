package com.hanwha.ai.global.exception;

import com.hanwha.ai.llm.exception.LlmRateLimitException;
import com.hanwha.ai.mcp.exception.AgentConfigurationAdminAccessException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(LlmRateLimitException.class)
    public ResponseEntity<ErrorResponse> handleLlmRateLimitException(
            LlmRateLimitException exception
    ) {
        log.warn("LLM rate limit exceeded. provider={}", exception.getProvider());
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ErrorResponse.of(exception.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        if (exception.getCause() == null) {
            log.warn("Business exception occurred: {}", exception.getMessage());
        } else {
            log.warn("Business exception occurred.", exception);
        }

        return ResponseEntity.badRequest().body(ErrorResponse.of(exception.getMessage()));
    }

    @ExceptionHandler(AgentConfigurationAdminAccessException.class)
    public ResponseEntity<ErrorResponse> handleAgentConfigurationAdminAccessException(
            AgentConfigurationAdminAccessException exception
    ) {
        log.warn("Agent configuration admin API unavailable. status={}", exception.status());
        return ResponseEntity
                .status(exception.status())
                .body(ErrorResponse.of(exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception exception) {
        log.error("Unexpected server error occurred.", exception);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("Unexpected server error."));
    }
}
