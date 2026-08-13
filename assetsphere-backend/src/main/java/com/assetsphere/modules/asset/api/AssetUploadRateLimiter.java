package com.assetsphere.modules.asset.api;

import java.util.UUID;

public interface AssetUploadRateLimiter {

    void check(UUID workspaceId, UUID userId);
}
