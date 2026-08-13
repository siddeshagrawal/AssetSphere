package com.assetsphere.modules.processing.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka-safe Processing event. Extracted content intentionally remains in Processing persistence and is never serialized here.
 */
public record AssetReadyForIntelligenceEvent(
        UUID eventId,
        UUID assetId,
        UUID assetVersionId,
        UUID workspaceId,
        Instant processingCompletedAt,
        int eventVersion
) {

    public static final String EVENT_TYPE = "asset.ready-for-intelligence.v1";
    public static final String TOPIC = "assets.ready-for-intelligence.v1";

    public AssetReadyForIntelligenceEvent {
        if (eventId == null || assetId == null || assetVersionId == null || workspaceId == null || processingCompletedAt == null) {
            throw new IllegalArgumentException("Intelligence ready event identifiers and completion time are required");
        }
        if (eventVersion != 1) {
            throw new IllegalArgumentException("Unsupported intelligence ready event version");
        }
    }

    public static AssetReadyForIntelligenceEvent create(
            UUID assetId, UUID assetVersionId, UUID workspaceId, Instant processingCompletedAt
    ) {
        return new AssetReadyForIntelligenceEvent(
                UUID.randomUUID(), assetId, assetVersionId, workspaceId, processingCompletedAt, 1
        );
    }
}
