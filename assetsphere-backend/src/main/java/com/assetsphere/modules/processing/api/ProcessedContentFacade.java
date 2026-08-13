package com.assetsphere.modules.processing.api;

import java.util.Optional;
import java.util.UUID;

public interface ProcessedContentFacade {

    Optional<ProcessedContent> findByAssetVersionId(UUID assetVersionId);
}
