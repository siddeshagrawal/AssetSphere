package com.assetsphere.modules.processing.consumer.application;

public class ProcessingLockUnavailableException extends RuntimeException {

    public ProcessingLockUnavailableException() {
        super("Asset version is already being processed");
    }
}
