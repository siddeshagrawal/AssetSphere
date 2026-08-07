package com.assetsphere.infrastructure.storage;

/** Internal translation of provider failures; provider details are retained only as the cause. */
class StorageAccessException extends RuntimeException {

    StorageAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
