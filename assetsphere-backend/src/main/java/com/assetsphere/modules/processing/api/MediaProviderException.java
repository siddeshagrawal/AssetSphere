package com.assetsphere.modules.processing.api;

public class MediaProviderException extends RuntimeException {
    private final boolean retryable;

    private MediaProviderException(String message, Throwable cause, boolean retryable) {
        super(message, cause); this.retryable = retryable;
    }

    public boolean isRetryable() { return retryable; }
    public static MediaProviderException retryable(String message, Throwable cause) { return new MediaProviderException(message, cause, true); }
    public static MediaProviderException nonRetryable(String message, Throwable cause) { return new MediaProviderException(message, cause, false); }
}
