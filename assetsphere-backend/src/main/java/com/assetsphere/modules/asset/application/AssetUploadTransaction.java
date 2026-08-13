package com.assetsphere.modules.asset.application;

import com.assetsphere.modules.asset.api.dto.response.AssetResponse;
import com.assetsphere.modules.asset.api.AssetUploadedEvent;
import com.assetsphere.modules.asset.api.AssetMetadataCache;
import com.assetsphere.modules.asset.domain.Asset;
import com.assetsphere.modules.asset.domain.AssetVersion;
import com.assetsphere.modules.asset.domain.AssetType;
import com.assetsphere.modules.asset.persistence.AssetRepository;
import com.assetsphere.modules.asset.persistence.AssetVersionRepository;
import com.assetsphere.modules.audit.api.AuditAction;
import com.assetsphere.modules.audit.api.AuditService;
import com.assetsphere.modules.storage.api.StorageFacade;
import com.assetsphere.modules.common.exception.ResourceNotFoundException;
import com.assetsphere.modules.billing.api.BillingEntitlementFacade;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;

@Service
@RequiredArgsConstructor
class AssetUploadTransaction {

    private final AssetRepository assetRepository;
    private final AssetVersionRepository assetVersionRepository;
    private final StorageFacade storageFacade;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;
    private final AssetIdempotencyService assetIdempotencyService;
    private final AssetMetadataCache assetMetadataCache;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;
    private final BillingEntitlementFacade billing;

    @Transactional
    AssetResponse persist(CreateAssetUploadCommand command) {
        billing.requireAssetUpload(command.workspaceId(), command.fileSize());
        StorageFacade.StorageObjectReference storageObject = storageFacade.attach(command.preparedStorageObject());
        try {
            if (storageObject.created()) {
                storageFacade.materializeCanonicalObject(command.preparedStorageObject());
            } else {
                storageFacade.discardTemporaryObject(command.preparedStorageObject());
            }
            Asset asset = assetRepository.save(Asset.initialUpload(
                command.workspaceId(),
                command.userId(),
                command.displayName(),
                command.description(),
                command.assetType()
        ));
        AssetVersion version = assetVersionRepository.save(AssetVersion.initial(
                asset.getId(),
                command.filename(),
                command.mimeType(),
                command.fileSize(),
                command.checksum(),
                storageObject.storageObjectId(),
                command.userId()
        ));
        AssetResponse response = AssetResponse.from(asset, version);
        AssetUploadedEvent event = new AssetUploadedEvent(
                UUID.randomUUID(),
                1,
                Instant.now(),
                asset.getId(),
                version.getId(),
                command.workspaceId(),
                command.userId(),
                command.filename(),
                command.mimeType(),
                command.fileSize(),
                command.checksum(),
                storageObject.objectKey(),
                version.getProcessingStatus()
        );
        eventPublisher.publishEvent(event);
        auditService.record(
                command.userId(),
                AuditAction.ASSET_UPLOADED,
                command.workspaceId(),
                "ASSET",
                asset.getId(),
                write(Map.of(
                        "assetId", asset.getId(),
                        "assetVersionId", version.getId(),
                        "fileName", command.filename(),
                        "mimeType", command.mimeType(),
                        "size", command.fileSize(),
                        "checksum", command.checksum(),
                        "processingStatus", version.getProcessingStatus()
                ))
        );
            assetIdempotencyService.complete(command.idempotencyRecordId(), response);
            entityManager.flush();
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            assetMetadataCache.evict(asset.getWorkspaceId(), asset.getId());
                        }
                    }
            );
            return response;
        } catch (RuntimeException exception) {
            if (storageObject.created()) {
                try {
                    // This runs before the transaction releases the unique-row lock, so no loser can own this canonical key.
                    storageFacade.rollbackCanonicalMaterialization(command.preparedStorageObject());
                } catch (RuntimeException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
            }
            throw exception;
        }
    }

    @Transactional
    AssetResponse persistVersion(CreateAssetVersionCommand command) {
        billing.requireStorage(command.workspaceId(), command.fileSize());
        Asset asset = assetRepository.findForUpdate(command.assetId(), command.workspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));
        StorageFacade.StorageObjectReference storageObject = storageFacade.attach(command.preparedStorageObject());
        try {
            if (storageObject.created()) {
                storageFacade.materializeCanonicalObject(command.preparedStorageObject());
            } else {
                storageFacade.discardTemporaryObject(command.preparedStorageObject());
            }
            int versionNumber = asset.appendVersion(command.assetType());
            AssetVersion version = assetVersionRepository.save(AssetVersion.subsequent(
                    asset.getId(), versionNumber, command.filename(), command.mimeType(), command.fileSize(),
                    command.checksum(), storageObject.storageObjectId(), command.userId()
            ));
            AssetResponse response = AssetResponse.from(asset, version);
            eventPublisher.publishEvent(new AssetUploadedEvent(
                    UUID.randomUUID(), 1, Instant.now(), asset.getId(), version.getId(), command.workspaceId(),
                    command.userId(), command.filename(), command.mimeType(), command.fileSize(), command.checksum(),
                    storageObject.objectKey(), version.getProcessingStatus()
            ));
            auditService.record(
                    command.userId(), AuditAction.ASSET_UPLOADED, command.workspaceId(), "ASSET", asset.getId(),
                    write(versionAuditMetadata(asset, version, command, versionNumber))
            );
            assetIdempotencyService.complete(command.idempotencyRecordId(), response);
            entityManager.flush();
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            assetMetadataCache.evict(asset.getWorkspaceId(), asset.getId());
                        }
                    }
            );
            return response;
        } catch (RuntimeException exception) {
            if (storageObject.created()) {
                try {
                    storageFacade.rollbackCanonicalMaterialization(command.preparedStorageObject());
                } catch (RuntimeException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
            }
            throw exception;
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize asset upload data", exception);
        }
    }

    private Map<String, Object> versionAuditMetadata(Asset asset, AssetVersion version,
                                                      CreateAssetVersionCommand command, int versionNumber) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "assetId", asset.getId());
        putIfPresent(metadata, "assetVersionId", version.getId());
        metadata.put("versionNumber", versionNumber);
        putIfPresent(metadata, "fileName", command.filename());
        putIfPresent(metadata, "mimeType", command.mimeType());
        metadata.put("size", command.fileSize());
        putIfPresent(metadata, "checksum", command.checksum());
        putIfPresent(metadata, "processingStatus", version.getProcessingStatus());
        return metadata;
    }

    private void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null) metadata.put(key, value);
    }

    record CreateAssetUploadCommand(
            UUID userId,
            UUID workspaceId,
            String filename,
            String displayName,
            String description,
            String mimeType,
            long fileSize,
            String checksum,
            AssetType assetType,
            UUID idempotencyRecordId,
            StorageFacade.PreparedStorageObject preparedStorageObject
    ) {
    }

    record CreateAssetVersionCommand(
            UUID userId,
            UUID workspaceId,
            UUID assetId,
            String filename,
            String mimeType,
            long fileSize,
            String checksum,
            AssetType assetType,
            UUID idempotencyRecordId,
            StorageFacade.PreparedStorageObject preparedStorageObject
    ) {
    }
}
