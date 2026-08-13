package com.assetsphere.modules.processing.api;

import java.time.Instant;
import java.util.UUID;

/** Kafka-safe signal that deterministic extraction and lexical indexing completed. */
public record AssetReadyForSemanticIndexEvent(UUID eventId, UUID workspaceId, UUID assetId,
                                              UUID assetVersionId, Instant processingCompletedAt, int eventVersion) {
    public static final String EVENT_TYPE = "asset.ready-for-semantic-index.v1";
    public static final String TOPIC = "assets.ready-for-semantic-index.v1";
    public AssetReadyForSemanticIndexEvent {
        if (eventId == null || workspaceId == null || assetId == null || assetVersionId == null || processingCompletedAt == null || eventVersion != 1) {
            throw new IllegalArgumentException("Semantic index event is invalid");
        }
    }
    public static AssetReadyForSemanticIndexEvent create(UUID workspaceId, UUID assetId, UUID assetVersionId, Instant completedAt) {
        return new AssetReadyForSemanticIndexEvent(UUID.randomUUID(), workspaceId, assetId, assetVersionId, completedAt, 1);
    }
}
