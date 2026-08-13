package com.assetsphere.modules.processing.api;

import java.util.UUID;

/** Coordinates concurrent consumers; PostgreSQL processed_events remains the durable idempotency boundary. */
public interface AssetProcessingLock {

    LockHandle tryAcquire(UUID assetVersionId);

    interface LockHandle extends AutoCloseable {
        boolean acquired();

        @Override
        void close();
    }
}
