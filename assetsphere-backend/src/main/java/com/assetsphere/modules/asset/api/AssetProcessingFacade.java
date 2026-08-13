package com.assetsphere.modules.asset.api;

import java.util.UUID;

/**
 * Narrow asset contract used by the Processing module after an outbox event is consumed.
 */
public interface AssetProcessingFacade {

    void queueUploadedAsset(UUID assetId, UUID assetVersionId);

    AssetProcessingInput beginProcessing(UUID assetId, UUID assetVersionId, String storageObjectKey);

    void completeProcessing(UUID assetId, UUID assetVersionId);

    void failProcessing(UUID assetId, UUID assetVersionId);

    void prepareFailedAssetForRetry(UUID assetId, UUID assetVersionId);

    void prepareProcessingAttemptRetry(UUID assetId, UUID assetVersionId);
}
