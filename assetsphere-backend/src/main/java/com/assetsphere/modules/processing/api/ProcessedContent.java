package com.assetsphere.modules.processing.api;

import java.util.UUID;

/** Immutable extracted-content projection; callers never receive Processing JPA entities. */
public record ProcessedContent(
        UUID workspaceId,
        UUID assetId,
        UUID assetVersionId,
        String extractedText,
        String extractionStatus,
        boolean truncated
) {

    public boolean hasUsableText() {
        return extractedText != null && !extractedText.isBlank() && "EXTRACTED".equals(extractionStatus);
    }
}
