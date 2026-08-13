package com.assetsphere.modules.search.api;

import java.util.UUID;

public interface SemanticSearchRateLimiter {
    void check(UUID workspaceId, UUID userId);
}
