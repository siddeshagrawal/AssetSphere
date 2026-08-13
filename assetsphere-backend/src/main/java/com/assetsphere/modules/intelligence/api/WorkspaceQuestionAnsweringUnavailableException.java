package com.assetsphere.modules.intelligence.api;

import com.assetsphere.modules.common.exception.ServiceUnavailableException;

public class WorkspaceQuestionAnsweringUnavailableException extends ServiceUnavailableException {

    public WorkspaceQuestionAnsweringUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
