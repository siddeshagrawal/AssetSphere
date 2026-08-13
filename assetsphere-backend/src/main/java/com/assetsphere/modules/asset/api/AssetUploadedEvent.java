package com.assetsphere.modules.asset.api;

import com.assetsphere.modules.asset.domain.AssetProcessingStatus;
import java.time.Instant;
import java.util.UUID;

/** Stable, binary-free contract written to the transactional outbox. */
public record AssetUploadedEvent(
        UUID eventId,
        int eventVersion,
        Instant occurredAt,
        UUID assetId,
        UUID assetVersionId,
        UUID workspaceId,
        UUID uploadedBy,
        String filename,
        String mimeType,
        long size,
        String checksum,
        String storageObjectKey,
        AssetProcessingStatus processingStatus
) {
}
