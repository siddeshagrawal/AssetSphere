package com.assetsphere.modules.search.api;

import java.util.UUID;

public interface SemanticIndexingLock {
    LockHandle tryAcquire(UUID assetVersionId);
    interface LockHandle extends AutoCloseable { boolean acquired(); @Override void close(); }
}
