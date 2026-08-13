package com.assetsphere.modules.intelligence.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AssetIntelligenceResponse(
        UUID assetId,
        UUID assetVersionId,
        String status,
        String summary,
        List<String> keyPoints,
        List<String> tags,
        String provider,
        String model,
        boolean inputTruncated,
        Instant generatedAt
) {
}
