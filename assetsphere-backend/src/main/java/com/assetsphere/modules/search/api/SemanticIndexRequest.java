package com.assetsphere.modules.search.api;

import java.time.Instant;
import java.util.UUID;

/** Search-owned identifier payload for semantic indexing orchestration. */
public record SemanticIndexRequest(
        UUID workspaceId,
        UUID assetId,
        UUID assetVersionId,
        Instant processingCompletedAt
) {

    public SemanticIndexRequest {
        if (workspaceId == null || assetId == null || assetVersionId == null || processingCompletedAt == null) {
            throw new IllegalArgumentException("Semantic index request identifiers are required");
        }
    }
}
