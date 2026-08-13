package com.assetsphere.modules.asset.api.dto.response;

import com.assetsphere.modules.asset.domain.Asset;
import com.assetsphere.modules.asset.domain.AssetProcessingStatus;
import com.assetsphere.modules.asset.domain.AssetVersion;
import java.time.Instant;
import java.util.UUID;

public record AssetVersionResponse(
        UUID assetVersionId,
        UUID assetId,
        int versionNumber,
        String originalFilename,
        String displayName,
        String mimeType,
        long fileSize,
        AssetProcessingStatus processingStatus,
        Instant createdAt
) {
    public static AssetVersionResponse from(Asset asset, AssetVersion version) {
        return new AssetVersionResponse(
                version.getId(), asset.getId(), version.getVersionNumber(), version.getOriginalFilename(),
                asset.getDisplayName(), version.getMimeType(), version.getFileSize(),
                version.getProcessingStatus(), version.getCreatedAt()
        );
    }
}
