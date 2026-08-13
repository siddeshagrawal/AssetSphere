package com.assetsphere.modules.asset.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAssetMetadataRequest(
        @NotBlank @Size(max = 255) String displayName,
        @Size(max = 2000) String description
) { }
