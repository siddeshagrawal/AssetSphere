package com.assetsphere.modules.intelligence.api;

import java.util.UUID;

public record DocumentIntelligenceRequest(
        UUID assetId,
        UUID assetVersionId,
        UUID workspaceId,
        String modelId,
        String filename,
        String mimeType,
        String content,
        boolean inputTruncated
) {
}
