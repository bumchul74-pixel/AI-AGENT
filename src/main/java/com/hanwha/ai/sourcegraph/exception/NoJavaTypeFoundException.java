package com.hanwha.ai.sourcegraph.exception;

public class NoJavaTypeFoundException extends RuntimeException {
    public NoJavaTypeFoundException(String message) {
        super(message);
    }
}