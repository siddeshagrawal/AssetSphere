package com.assetsphere.modules.asset.api;

import java.util.UUID;

public record AssetMetadataUpdatedEvent(
        UUID workspaceId, UUID assetId, String displayName, String description
) { }
