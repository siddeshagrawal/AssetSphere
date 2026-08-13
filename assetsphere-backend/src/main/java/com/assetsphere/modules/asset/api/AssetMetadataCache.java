package com.assetsphere.modules.asset.api;

import java.util.Optional;
import java.util.UUID;

public interface AssetMetadataCache {

    Optional<AssetMetadataSnapshot> get(UUID workspaceId, UUID assetId);

    void put(AssetMetadataSnapshot response);

    void evict(UUID workspaceId, UUID assetId);
}
