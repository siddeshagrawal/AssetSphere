package com.assetsphere.modules.asset.api;

import java.util.UUID;

/** Read contract for internal modules after they have enforced their own workspace authorization. */
public interface AssetReadFacade {

    AssetMetadataSnapshot getMetadata(UUID workspaceId, UUID assetId);

    AssetMetadataSnapshot getVersionMetadata(UUID workspaceId, UUID assetId, int versionNumber);

    AssetMetadataSnapshot getVersionMetadata(UUID workspaceId, UUID assetId, UUID assetVersionId);
}
