package com.assetsphere.modules.intelligence.api.dto.request;

import jakarta.validation.constraints.Min;

public record CompareAssetVersionsRequest(
        @Min(1) int fromVersion,
        @Min(1) int toVersion,
        String modelId
) { }
