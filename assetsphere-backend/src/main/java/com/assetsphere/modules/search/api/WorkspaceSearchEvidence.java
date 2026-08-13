package com.assetsphere.modules.search.api;

import java.util.UUID;

public record WorkspaceSearchEvidence(
        UUID assetId,
        UUID assetVersionId,
        String title,
        String filename,
        Integer chunkOrdinal,
        String text
) {
}
