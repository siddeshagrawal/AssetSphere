package com.assetsphere.modules.intelligence.api;

import java.util.UUID;

/** Coordinates a paid provider invocation across application nodes; PostgreSQL remains the durable boundary. */
public interface IntelligenceProcessingLock {

    LockHandle tryAcquire(UUID assetVersionId);

    interface LockHandle extends AutoCloseable {
        boolean acquired();

        @Override
        void close();
    }
}
