package com.gestor.financeiro.exception;

import org.springframework.http.HttpStatus;

public class AssistantException extends RuntimeException {
    private final String code;
    private final HttpStatus status;
    private final Integer retryAfterSeconds;

    public AssistantException(String code, String message, HttpStatus status) {
        this(code, message, status, null);
    }
    public AssistantException(String code, String message, HttpStatus status, Integer retryAfterSeconds) {
        super(message); this.code = code; this.status = status; this.retryAfterSeconds = retryAfterSeconds;
    }
    public String code() { return code; }
    public HttpStatus status() { return status; }
    public Integer retryAfterSeconds() { return retryAfterSeconds; }
}
