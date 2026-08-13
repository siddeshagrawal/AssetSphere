package com.assetsphere.modules.asset.application;

import com.assetsphere.modules.asset.api.AssetUploadProperties;

import com.assetsphere.modules.asset.api.dto.response.AssetResponse;
import com.assetsphere.modules.asset.domain.AssetLifecycleStatus;
import com.assetsphere.modules.asset.domain.AssetProcessingStatus;
import com.assetsphere.modules.asset.domain.AssetType;
import com.assetsphere.modules.asset.domain.IdempotencyRecord;
import com.assetsphere.modules.asset.persistence.IdempotencyRecordRepository;
import com.assetsphere.modules.common.exception.ConflictException;
import com.assetsphere.modules.common.time.ClockProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetIdempotencyServiceTests {

    private static final Instant NOW = Instant.parse("2026-08-07T00:00:00Z");

    @Mock private IdempotencyRecordRepository records;
    @Mock private AssetIdempotencyReservationTransaction reservationTransaction;
    @Mock private AssetIdempotencyFailureTransaction failureTransaction;

    @Test
    void retriesFailedRecordWithSameFingerprintWithoutCreatingAnotherRecord() {
        IdempotencyRecord record = failedRecord("fingerprint");
        when(records.findByUserIdAndWorkspaceIdAndOperationTypeAndIdempotencyKey(
                record.getUserId(), record.getWorkspaceId(), "ASSET_UPLOAD", "key"
        )).thenReturn(Optional.of(record));
        when(reservationTransaction.retry(eq(record.getId()), any())).thenAnswer(invocation -> {
            record.retry(invocation.getArgument(1));
            return record;
        });

        AssetIdempotencyService.Reservation reservation = service().reserve(
                record.getUserId(), record.getWorkspaceId(), "key", "fingerprint"
        );

        assertThat(reservation.recordId()).isEqualTo(record.getId());
        assertThat(record.getStatus().name()).isEqualTo("IN_PROGRESS");
        assertThat(record.getExpiresAt()).isEqualTo(NOW.plusSeconds(900));
        verify(reservationTransaction, never()).reserve(any());
    }

    @Test
    void rejectsFailedRecordWhenFingerprintChanges() {
        IdempotencyRecord record = failedRecord("fingerprint");
        when(records.findByUserIdAndWorkspaceIdAndOperationTypeAndIdempotencyKey(
                record.getUserId(), record.getWorkspaceId(), "ASSET_UPLOAD", "key"
        )).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service().reserve(record.getUserId(), record.getWorkspaceId(), "key", "other"))
                .isInstanceOf(ConflictException.class);
        verify(reservationTransaction, never()).retry(any(), any());
    }

    @Test
    void replaysCompletedRecordAndRejectsActiveInProgressRecord() throws Exception {
        IdempotencyRecord completed = IdempotencyRecord.reserve(
                "key", UUID.randomUUID(), UUID.randomUUID(), "fingerprint", NOW.plusSeconds(900)
        );
        AssetResponse response = response(completed.getWorkspaceId());
        completed.complete(response.assetId(), 201, new ObjectMapper().findAndRegisterModules().writeValueAsString(response), NOW.plusSeconds(3600));
        when(records.findByUserIdAndWorkspaceIdAndOperationTypeAndIdempotencyKey(
                completed.getUserId(), completed.getWorkspaceId(), "ASSET_UPLOAD", "key"
        )).thenReturn(Optional.of(completed));

        AssetIdempotencyService.Reservation replay = service().reserve(
                completed.getUserId(), completed.getWorkspaceId(), "key", "fingerprint"
        );

        assertThat(replay.isReplay()).isTrue();
        assertThat(replay.replayResponse()).isEqualTo(response);

        IdempotencyRecord active = IdempotencyRecord.reserve(
                "active", UUID.randomUUID(), UUID.randomUUID(), "fingerprint", NOW.plusSeconds(900)
        );
        when(records.findByUserIdAndWorkspaceIdAndOperationTypeAndIdempotencyKey(
                active.getUserId(), active.getWorkspaceId(), "ASSET_UPLOAD", "active"
        )).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service().reserve(active.getUserId(), active.getWorkspaceId(), "active", "fingerprint"))
                .isInstanceOf(ConflictException.class);
    }

    private AssetIdempotencyService service() {
        AssetUploadProperties properties = new AssetUploadProperties();
        ClockProvider clock = () -> NOW;
        return new AssetIdempotencyService(
                records, reservationTransaction, failureTransaction, properties, clock, new ObjectMapper().findAndRegisterModules()
        );
    }

    private IdempotencyRecord failedRecord(String fingerprint) {
        IdempotencyRecord record = IdempotencyRecord.reserve(
                "key", UUID.randomUUID(), UUID.randomUUID(), fingerprint, NOW.plusSeconds(900)
        );
        record.markFailed(NOW.plusSeconds(900));
        return record;
    }

    private AssetResponse response(UUID workspaceId) {
        return new AssetResponse(UUID.randomUUID(), UUID.randomUUID(), workspaceId, "report.pdf", "Report", null,
                AssetType.PDF, "application/pdf", 1, "checksum", 1, AssetLifecycleStatus.ACTIVE,
                AssetProcessingStatus.UPLOADED, NOW);
    }
}
