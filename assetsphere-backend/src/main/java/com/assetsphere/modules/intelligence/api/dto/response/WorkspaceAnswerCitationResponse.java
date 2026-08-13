package com.assetsphere.modules.intelligence.api.dto.response;

import java.util.UUID;

public record WorkspaceAnswerCitationResponse(
        String sourceId,
        UUID assetId,
        UUID assetVersionId,
        String title,
        String filename,
        Integer chunkOrdinal,
        String snippet
) {
}
