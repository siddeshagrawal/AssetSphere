package com.assetsphere.modules.search.api;

import java.util.UUID;

/** Stable, persistence-free input for upserting one searchable Asset version. */
public record SearchIndexCommand(
        UUID workspaceId,
        UUID assetId,
        UUID assetVersionId,
        String displayName,
        String originalFilename,
        String description,
        String mimeType,
        String processingStatus,
        String extractedText
) {
}
