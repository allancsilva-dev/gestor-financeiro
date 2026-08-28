package com.gestor.financeiro.service.assistant;

public class ProviderFailure extends RuntimeException {
    public enum Kind { RETRYABLE, SCHEMA, SAFETY_REFUSAL, CONFIGURATION }
    private final Kind kind;
    private final long retryAfterMillis;

    public ProviderFailure(Kind kind, String message) { this(kind, message, 0, null); }
    public ProviderFailure(Kind kind, String message, long retryAfterMillis, Throwable cause) {
        super(message, cause); this.kind = kind; this.retryAfterMillis = Math.max(0, retryAfterMillis);
    }
    public Kind kind() { return kind; }
    public long retryAfterMillis() { return retryAfterMillis; }
    public boolean allowsFailover() { return kind == Kind.RETRYABLE || kind == Kind.SCHEMA; }
}
