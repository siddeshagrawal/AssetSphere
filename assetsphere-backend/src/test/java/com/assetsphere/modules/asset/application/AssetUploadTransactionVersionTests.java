package com.assetsphere.modules.asset.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetsphere.modules.asset.api.AssetMetadataCache;
import com.assetsphere.modules.asset.api.AssetUploadedEvent;
import com.assetsphere.modules.asset.domain.Asset;
import com.assetsphere.modules.asset.domain.AssetType;
import com.assetsphere.modules.asset.domain.AssetVersion;
import com.assetsphere.modules.asset.persistence.AssetRepository;
import com.assetsphere.modules.asset.persistence.AssetVersionRepository;
import com.assetsphere.modules.audit.api.AuditService;
import com.assetsphere.modules.storage.api.StorageFacade;
import com.assetsphere.modules.billing.api.BillingEntitlementFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AssetUploadTransactionVersionTests {

    @Test
    void appendsVersionTwoThenThreeAndPublishesEachForProcessing() {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Asset asset = Asset.initialUpload(workspaceId, userId, "Report", null, AssetType.PDF);
        ReflectionTestUtils.setField(asset, "id", assetId);
        AssetRepository assets = mock(AssetRepository.class);
        AssetVersionRepository versions = mock(AssetVersionRepository.class);
        StorageFacade storage = mock(StorageFacade.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        when(assets.findForUpdate(assetId, workspaceId)).thenReturn(Optional.of(asset));
        when(versions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storage.attach(any())).thenReturn(
                new StorageFacade.StorageObjectReference(UUID.randomUUID(), "workspaces/object", false)
        );
        AssetUploadTransaction transaction = new AssetUploadTransaction(
                assets, versions, storage, events, mock(AuditService.class), mock(AssetIdempotencyService.class),
                mock(AssetMetadataCache.class), new ObjectMapper(), mock(EntityManager.class),
                mock(BillingEntitlementFacade.class)
        );
        StorageFacade.PreparedStorageObject prepared = new StorageFacade.PreparedStorageObject(
                workspaceId, "checksum", "workspaces/object", null, "MINIO", "application/pdf", 1, false, true
        );

        TransactionSynchronizationManager.initSynchronization();
        try {
            transaction.persistVersion(command(userId, workspaceId, assetId, UUID.randomUUID(), prepared));
            transaction.persistVersion(command(userId, workspaceId, assetId, UUID.randomUUID(), prepared));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        ArgumentCaptor<AssetVersion> savedVersions = ArgumentCaptor.forClass(AssetVersion.class);
        verify(versions, times(2)).save(savedVersions.capture());
        assertThat(savedVersions.getAllValues()).extracting(AssetVersion::getVersionNumber).containsExactly(2, 3);
        assertThat(asset.getLatestVersionNumber()).isEqualTo(3);
        verify(events, times(2)).publishEvent(any(AssetUploadedEvent.class));
    }

    private AssetUploadTransaction.CreateAssetVersionCommand command(
            UUID userId, UUID workspaceId, UUID assetId, UUID idempotencyRecordId,
            StorageFacade.PreparedStorageObject prepared
    ) {
        return new AssetUploadTransaction.CreateAssetVersionCommand(
                userId, workspaceId, assetId, "report.pdf", "application/pdf", 1,
                "checksum", AssetType.PDF, idempotencyRecordId, prepared
        );
    }
}
