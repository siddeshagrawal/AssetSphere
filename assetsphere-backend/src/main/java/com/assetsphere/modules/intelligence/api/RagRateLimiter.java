package com.assetsphere.modules.intelligence.api;

import java.util.UUID;

public interface RagRateLimiter {

    void check(UUID workspaceId, UUID userId);
}
