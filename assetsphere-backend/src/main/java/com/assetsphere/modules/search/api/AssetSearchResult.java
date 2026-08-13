package com.assetsphere.modules.search.api;

import java.util.UUID;

public record AssetSearchResult(
        UUID assetId,
        UUID assetVersionId,
        int versionNumber,
        String displayName,
        String originalFilename,
        String mimeType,
        String processingStatus,
        double rank,
        String snippet
) {
}
