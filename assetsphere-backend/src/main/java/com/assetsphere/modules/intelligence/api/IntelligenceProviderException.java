package com.assetsphere.modules.intelligence.api;

/** A provider failure classified for Kafka retry policy without exposing provider details to an API client. */
public class IntelligenceProviderException extends RuntimeException {

    private final boolean retryable;

    private IntelligenceProviderException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public static IntelligenceProviderException retryable(String message, Throwable cause) {
        return new IntelligenceProviderException(message, true, cause);
    }

    public static IntelligenceProviderException nonRetryable(String message, Throwable cause) {
        return new IntelligenceProviderException(message, false, cause);
    }

    public boolean isRetryable() {
        return retryable;
    }
}
