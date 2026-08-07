package com.assetsphere.modules.common.exception;

public class AuthorizationDeniedException extends RuntimeException {
    public AuthorizationDeniedException(String message) {
        super(message);
    }
}
