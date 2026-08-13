package com.assetsphere.modules.search.api;

public class SemanticIndexingException extends RuntimeException {
    private final boolean retryable;
    private SemanticIndexingException(String message, boolean retryable, Throwable cause) { super(message, cause); this.retryable = retryable; }
    public static SemanticIndexingException retryable(String message, Throwable cause) { return new SemanticIndexingException(message, true, cause); }
    public static SemanticIndexingException terminal(String message, Throwable cause) { return new SemanticIndexingException(message, false, cause); }
    public boolean isRetryable() { return retryable; }
}
