package com.assetsphere.modules.asset.api;

import com.assetsphere.modules.asset.api.dto.response.AssetResponse;
import com.assetsphere.modules.asset.domain.AssetLifecycleStatus;
import com.assetsphere.modules.asset.domain.AssetProcessingStatus;
import com.assetsphere.modules.asset.domain.AssetType;
import java.time.Instant;
import java.util.UUID;

/**
 * Stable cache value; it deliberately contains no JPA entity or authorization state.
 */
public record AssetMetadataSnapshot(
        UUID assetId, UUID assetVersionId, UUID workspaceId, String originalFilename, String displayName, String description,
        AssetType assetType, String mimeType, long fileSize, String checksum, int versionNumber,
        AssetLifecycleStatus lifecycleStatus, AssetProcessingStatus processingStatus, Instant createdAt
) {

    public boolean readyForIntelligence() {
        return processingStatus == AssetProcessingStatus.READY
                || processingStatus == AssetProcessingStatus.PARTIALLY_PROCESSED;
    }

    public AssetResponse toResponse() {
        return new AssetResponse(
                assetId,
                assetVersionId,
                workspaceId,
                originalFilename,
                displayName,
                description,
                assetType,
                mimeType,
                fileSize,
                checksum,
                versionNumber,
                lifecycleStatus,
                processingStatus,
                createdAt
        );
    }
}
