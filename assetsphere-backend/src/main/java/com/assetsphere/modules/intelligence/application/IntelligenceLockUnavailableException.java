package com.assetsphere.modules.intelligence.application;

public class IntelligenceLockUnavailableException extends RuntimeException {

    public IntelligenceLockUnavailableException() {
        super("Asset intelligence is already being processed");
    }
}
