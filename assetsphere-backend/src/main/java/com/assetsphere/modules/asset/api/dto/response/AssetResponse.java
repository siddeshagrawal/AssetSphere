package com.assetsphere.modules.asset.api.dto.response;

import com.assetsphere.modules.asset.domain.Asset;
import com.assetsphere.modules.asset.domain.AssetLifecycleStatus;
import com.assetsphere.modules.asset.domain.AssetProcessingStatus;
import com.assetsphere.modules.asset.domain.AssetType;
import com.assetsphere.modules.asset.domain.AssetVersion;
import java.time.Instant;
import java.util.UUID;

public record AssetResponse(
        UUID assetId,
        UUID assetVersionId,
        UUID workspaceId,
        String originalFilename,
        String displayName,
        String description,
        AssetType assetType,
        String mimeType,
        long fileSize,
        String checksum,
        int versionNumber,
        AssetLifecycleStatus lifecycleStatus,
        AssetProcessingStatus processingStatus,
        Instant createdAt
) {

    public static AssetResponse from(Asset asset, AssetVersion version) {
        return new AssetResponse(
                asset.getId(),
                version.getId(),
                asset.getWorkspaceId(),
                version.getOriginalFilename(),
                asset.getDisplayName(),
                asset.getDescription(),
                asset.getAssetType(),
                version.getMimeType(),
                version.getFileSize(),
                version.getChecksumSha256(),
                version.getVersionNumber(),
                asset.getLifecycleStatus(),
                version.getProcessingStatus(),
                asset.getCreatedAt()
        );
    }
}
