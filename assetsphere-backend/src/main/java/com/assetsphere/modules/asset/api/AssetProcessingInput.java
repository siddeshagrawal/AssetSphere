package com.assetsphere.modules.asset.api;

import java.util.UUID;

/** Immutable processing input. Processing never receives Asset JPA entities. */
public record AssetProcessingInput(
        UUID workspaceId,
        UUID assetId,
        UUID assetVersionId,
        String storageObjectKey,
        String displayName,
        String description,
        String originalFilename,
        String mimeType,
        long fileSize
) {
}
