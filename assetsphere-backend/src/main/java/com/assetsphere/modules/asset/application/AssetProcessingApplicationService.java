package com.assetsphere.modules.asset.application;

import com.assetsphere.modules.asset.api.AssetProcessingFacade;
import com.assetsphere.modules.asset.api.AssetProcessingInput;
import com.assetsphere.modules.asset.api.AssetMetadataCache;
import com.assetsphere.modules.asset.domain.Asset;
import com.assetsphere.modules.asset.domain.AssetVersion;
import com.assetsphere.modules.asset.persistence.AssetRepository;
import com.assetsphere.modules.asset.persistence.AssetVersionRepository;
import com.assetsphere.modules.common.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class AssetProcessingApplicationService implements AssetProcessingFacade {

    private final AssetRepository assets;
    private final AssetVersionRepository assetVersions;
    private final AssetMetadataCache assetMetadataCache;

    @Override
    @Transactional
    public void queueUploadedAsset(UUID assetId, UUID assetVersionId) {
        AssetAndVersion aggregate = load(assetId, assetVersionId);
        Asset asset = aggregate.asset();
        AssetVersion version = aggregate.version();
        asset.queueForProcessing();
        version.queueForProcessing();
        evictMetadataAfterCommit(asset);
    }

    @Override
    @Transactional
    public AssetProcessingInput beginProcessing(UUID assetId, UUID assetVersionId, String storageObjectKey) {
        if (storageObjectKey == null || storageObjectKey.isBlank()) {
            throw new ResourceNotFoundException("Storage object not found");
        }
        AssetAndVersion aggregate = load(assetId, assetVersionId);
        aggregate.asset().beginProcessing();
        aggregate.version().beginProcessing();
        evictMetadataAfterCommit(aggregate.asset());
        return new AssetProcessingInput(
                aggregate.asset().getWorkspaceId(),
                aggregate.asset().getId(),
                aggregate.version().getId(),
                storageObjectKey,
                aggregate.asset().getDisplayName(),
                aggregate.asset().getDescription(),
                aggregate.version().getOriginalFilename(),
                aggregate.version().getMimeType(),
                aggregate.version().getFileSize()
        );
    }

    @Override
    @Transactional
    public void completeProcessing(UUID assetId, UUID assetVersionId) {
        AssetAndVersion aggregate = load(assetId, assetVersionId);
        aggregate.asset().completeProcessing();
        aggregate.version().completeProcessing();
        evictMetadataAfterCommit(aggregate.asset());
    }

    @Override
    @Transactional
    public void failProcessing(UUID assetId, UUID assetVersionId) {
        AssetAndVersion aggregate = load(assetId, assetVersionId);
        if (aggregate.asset().getProcessingStatus() != com.assetsphere.modules.asset.domain.AssetProcessingStatus.FAILED) {
            aggregate.asset().queueForProcessing();
            aggregate.asset().failProcessing();
        }
        if (aggregate.version().getProcessingStatus() != com.assetsphere.modules.asset.domain.AssetProcessingStatus.FAILED) {
            aggregate.version().queueForProcessing();
            aggregate.version().failProcessing();
        }
        evictMetadataAfterCommit(aggregate.asset());
    }

    @Override
    @Transactional
    public void prepareFailedAssetForRetry(UUID assetId, UUID assetVersionId) {
        AssetAndVersion aggregate = load(assetId, assetVersionId);
        aggregate.asset().prepareProcessingRetry();
        aggregate.version().prepareProcessingRetry();
        evictMetadataAfterCommit(aggregate.asset());
    }

    @Override
    @Transactional
    public void prepareProcessingAttemptRetry(UUID assetId, UUID assetVersionId) {
        AssetAndVersion aggregate = load(assetId, assetVersionId);
        aggregate.asset().prepareProcessingAttemptRetry();
        aggregate.version().prepareProcessingAttemptRetry();
        evictMetadataAfterCommit(aggregate.asset());
    }

    private AssetAndVersion load(UUID assetId, UUID assetVersionId) {
        Asset asset = assets.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));
        AssetVersion version = assetVersions.findById(assetVersionId)
                .filter(candidate -> candidate.getAssetId().equals(asset.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Asset version not found"));
        return new AssetAndVersion(asset, version);
    }

    private void evictMetadataAfterCommit(Asset asset) {
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        assetMetadataCache.evict(asset.getWorkspaceId(), asset.getId());
                    }
                }
        );
    }

    private record AssetAndVersion(Asset asset, AssetVersion version) {
    }
}
