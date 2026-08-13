package com.assetsphere.modules.asset.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetsphere.modules.asset.api.dto.response.AssetResponse;
import com.assetsphere.modules.asset.api.AssetUploadRateLimiter;
import com.assetsphere.modules.asset.domain.AssetLifecycleStatus;
import com.assetsphere.modules.asset.domain.AssetProcessingStatus;
import com.assetsphere.modules.asset.domain.AssetType;
import com.assetsphere.modules.asset.persistence.AssetRepository;
import com.assetsphere.modules.common.security.CurrentUser;
import com.assetsphere.modules.common.security.CurrentUserProvider;
import com.assetsphere.modules.storage.api.StorageFacade;
import com.assetsphere.modules.workspace.api.WorkspaceAccessFacade;
import com.assetsphere.modules.billing.api.BillingEntitlementFacade;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class AssetUploadServiceTests {

    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private WorkspaceAccessFacade workspaceAccess;
    @Mock private AssetUploadRateLimiter rateLimiter;
    @Mock private AssetFileValidator fileValidator;
    @Mock private AssetChecksum checksum;
    @Mock private UploadFingerprint fingerprint;
    @Mock private AssetIdempotencyService idempotencyService;
    @Mock private StorageFacade storageFacade;
    @Mock private AssetUploadTransaction uploadTransaction;
    @Mock private AssetRepository assetRepository;
    @Mock private BillingEntitlementFacade billing;

    @Test
    void completedReservationReplaysWithoutStorageOrPersistence() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        AssetResponse response = response(workspaceId);
        when(currentUserProvider.requireCurrentUser()).thenReturn(new CurrentUser(userId, "user@example.com"));
        when(fileValidator.validate(any())).thenReturn(validatedFile());
        when(checksum.sha256(any())).thenReturn("checksum");
        when(fingerprint.create(eq(userId), eq(workspaceId), any(), any(), anyLong(), any(), any(), any()))
                .thenReturn("fingerprint");
        when(idempotencyService.reserve(userId, workspaceId, "key", "fingerprint"))
                .thenReturn(AssetIdempotencyService.Reservation.replay(response));

        AssetUploadService.UploadResult result = service().upload(workspaceId, "key", multipartFile(), null, null);

        assertThat(result.replayed()).isTrue();
        assertThat(result.response()).isEqualTo(response);
        verify(storageFacade, never()).prepare(any());
        verify(uploadTransaction, never()).persist(any());
        verify(billing, never()).requireAssetUpload(any(), anyLong());
    }

    @Test
    void newReservationPreparesStorageAndPersistsAsset() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID idempotencyRecordId = UUID.randomUUID();
        AssetResponse response = response(workspaceId);
        StorageFacade.PreparedStorageObject prepared = new StorageFacade.PreparedStorageObject(
                workspaceId, "checksum", "workspaces/canonical", "workspaces/tmp/request", "MINIO", "application/pdf", 1, true, false
        );
        when(currentUserProvider.requireCurrentUser()).thenReturn(new CurrentUser(userId, "user@example.com"));
        when(fileValidator.validate(any())).thenReturn(validatedFile());
        when(checksum.sha256(any())).thenReturn("checksum");
        when(fingerprint.create(eq(userId), eq(workspaceId), any(), any(), anyLong(), any(), any(), any()))
                .thenReturn("fingerprint");
        when(idempotencyService.reserve(userId, workspaceId, "key", "fingerprint"))
                .thenReturn(AssetIdempotencyService.Reservation.newReservation(idempotencyRecordId));
        when(storageFacade.prepare(any())).thenReturn(prepared);
        when(uploadTransaction.persist(any())).thenReturn(response);

        AssetUploadService.UploadResult result = service().upload(workspaceId, "key", multipartFile(), "Report", "Description");

        assertThat(result.replayed()).isFalse();
        verify(workspaceAccess).requireActiveMembership(workspaceId, userId);
        verify(billing).requireAssetUpload(workspaceId, 1);
        verify(storageFacade).prepare(any());
        ArgumentCaptor<AssetUploadTransaction.CreateAssetUploadCommand> command = ArgumentCaptor.forClass(
                AssetUploadTransaction.CreateAssetUploadCommand.class
        );
        verify(uploadTransaction).persist(command.capture());
        assertThat(command.getValue().idempotencyRecordId()).isEqualTo(idempotencyRecordId);
        assertThat(command.getValue().preparedStorageObject()).isEqualTo(prepared);
    }

    @Test
    void completedVersionReservationReplaysWithoutCreatingAnotherVersion() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        AssetResponse response = response(workspaceId);
        when(currentUserProvider.requireCurrentUser()).thenReturn(new CurrentUser(userId, "user@example.com"));
        when(assetRepository.existsByIdAndWorkspaceId(assetId, workspaceId)).thenReturn(true);
        when(fileValidator.validate(any())).thenReturn(validatedFile());
        when(checksum.sha256(any())).thenReturn("checksum");
        when(fingerprint.createVersion(userId, workspaceId, assetId, "report.pdf", "application/pdf", 1, "checksum"))
                .thenReturn("fingerprint");
        when(idempotencyService.reserveVersion(userId, workspaceId, "key", "fingerprint"))
                .thenReturn(AssetIdempotencyService.Reservation.replay(response));

        AssetUploadService.UploadResult result = service().uploadVersion(workspaceId, assetId, "key", multipartFile());

        assertThat(result.replayed()).isTrue();
        verify(storageFacade, never()).prepare(any());
        verify(uploadTransaction, never()).persistVersion(any());
    }

    private AssetUploadService service() {
        return new AssetUploadService(currentUserProvider, workspaceAccess, rateLimiter, fileValidator, checksum, fingerprint,
                idempotencyService, storageFacade, uploadTransaction, assetRepository, billing);
    }

    private AssetFileValidator.ValidatedFile validatedFile() {
        return new AssetFileValidator.ValidatedFile("report.pdf", "application/pdf", 1, AssetType.PDF);
    }

    private MockMultipartFile multipartFile() {
        return new MockMultipartFile("file", "report.pdf", "application/pdf", new byte[] {1});
    }

    private AssetResponse response(UUID workspaceId) {
        return new AssetResponse(UUID.randomUUID(), UUID.randomUUID(), workspaceId, "report.pdf", "Report", null,
                AssetType.PDF, "application/pdf", 1, "checksum", 1, AssetLifecycleStatus.ACTIVE,
                AssetProcessingStatus.UPLOADED, Instant.parse("2026-08-07T00:00:00Z"));
    }
}
