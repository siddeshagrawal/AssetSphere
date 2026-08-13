package com.assetsphere.infrastructure.storage;

import com.assetsphere.modules.common.exception.ServiceUnavailableException;

/**
 * Internal translation of provider failures; provider details are retained only as the cause.
 */
class StorageAccessException extends ServiceUnavailableException {

    StorageAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
