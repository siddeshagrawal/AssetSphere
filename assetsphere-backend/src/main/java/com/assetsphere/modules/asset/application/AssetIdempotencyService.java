package com.assetsphere.modules.asset.application;

import com.assetsphere.modules.asset.api.dto.response.AssetResponse;
import com.assetsphere.modules.asset.api.AssetUploadProperties;
import com.assetsphere.modules.asset.domain.IdempotencyRecord;
import com.assetsphere.modules.asset.domain.IdempotencyStatus;
import com.assetsphere.modules.asset.persistence.IdempotencyRecordRepository;
import com.assetsphere.modules.common.exception.ConflictException;
import com.assetsphere.modules.common.exception.InvalidRequestException;
import com.assetsphere.modules.common.time.ClockProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class AssetIdempotencyService {

    private static final String OPERATION = "ASSET_UPLOAD";

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final AssetIdempotencyReservationTransaction assetIdempotencyReservationTransaction;
    private final AssetIdempotencyFailureTransaction assetIdempotencyFailureTransaction;
    private final AssetUploadProperties assetUploadProperties;
    private final ClockProvider clockProvider;
    private final ObjectMapper objectMapper;

    Reservation reserve(UUID userId, UUID workspaceId, String key, String fingerprint) {
        return reserve(userId, workspaceId, OPERATION, key, fingerprint);
    }

    Reservation reserveVersion(UUID userId, UUID workspaceId, String key, String fingerprint) {
        return reserve(userId, workspaceId, "ASSET_VERSION_UPLOAD", key, fingerprint);
    }

    private Reservation reserve(UUID userId, UUID workspaceId, String operation, String key, String fingerprint) {
        validateKey(key);
        Instant now = clockProvider.now();
        IdempotencyRecord existing = idempotencyRecordRepository
                .findByUserIdAndWorkspaceIdAndOperationTypeAndIdempotencyKey(userId, workspaceId, operation, key)
                .orElse(null);

        if (existing != null) {
            return resolveExisting(existing, fingerprint, now);
        }

        return createReservation(userId, workspaceId, operation, key, fingerprint, now);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void complete(UUID recordId, AssetResponse response) {
        IdempotencyRecord record = idempotencyRecordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalStateException("Reserved idempotency record is missing"));
        record.complete(
                response.assetId(),
                201,
                write(response),
                clockProvider.now().plus(assetUploadProperties.getIdempotencyCompletedTtl())
        );
    }

    void markFailed(UUID recordId) {
        assetIdempotencyFailureTransaction.markFailed(recordId, clockProvider.now().plus(assetUploadProperties.getIdempotencyFailedTtl()));
    }

    private Reservation resolveExisting(IdempotencyRecord idempotencyRecord, String fingerprint, Instant now) {
        if (idempotencyRecord.isExpired(now)) {
            assetIdempotencyReservationTransaction.delete(idempotencyRecord);
            return createReservation(idempotencyRecord.getUserId(), idempotencyRecord.getWorkspaceId(),
                    idempotencyRecord.getOperationType(), idempotencyRecord.getIdempotencyKey(), fingerprint, now);
        }
        if (!idempotencyRecord.getRequestFingerprint().equals(fingerprint)) {
            throw new ConflictException("Idempotency key was already used for a different request");
        }
        if (idempotencyRecord.getStatus() == IdempotencyStatus.COMPLETED) {
            return Reservation.replay(read(idempotencyRecord.getResponseBody()));
        }
        if (idempotencyRecord.getStatus() == IdempotencyStatus.IN_PROGRESS) {
            throw new ConflictException("An upload with this idempotency key is already in progress");
        }
        if (idempotencyRecord.getStatus() == IdempotencyStatus.FAILED) {
            IdempotencyRecord retry = assetIdempotencyReservationTransaction.retry(
                    idempotencyRecord.getId(), now.plus(assetUploadProperties.getIdempotencyInProgressTtl())
            );
            return Reservation.newReservation(retry.getId());
        }
        throw new IllegalStateException("Unsupported idempotency status");
    }

    private Reservation createReservation(UUID userId, UUID workspaceId, String operation, String key,
                                          String fingerprint, Instant now) {
        try {
            IdempotencyRecord record = assetIdempotencyReservationTransaction.reserve(IdempotencyRecord.reserve(
                    key, userId, workspaceId, operation, fingerprint,
                    now.plus(assetUploadProperties.getIdempotencyInProgressTtl())
            ));
            return Reservation.newReservation(record.getId());
        } catch (DataIntegrityViolationException exception) {
            IdempotencyRecord concurrentRecord = idempotencyRecordRepository
                    .findByUserIdAndWorkspaceIdAndOperationTypeAndIdempotencyKey(userId, workspaceId, operation, key)
                    .orElseThrow(() -> exception);
            return resolveExisting(concurrentRecord, fingerprint, now);
        }
    }

    private void validateKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new InvalidRequestException("Idempotency-Key must contain 1 to 128 safe characters");
        }
    }

    private String write(AssetResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize idempotent response", exception);
        }
    }

    private AssetResponse read(String responseBody) {
        try {
            return objectMapper.readValue(responseBody, AssetResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored idempotent response is invalid", exception);
        }
    }

    record Reservation(UUID recordId, AssetResponse replayResponse) {

        static Reservation newReservation(UUID recordId) {
            return new Reservation(recordId, null);
        }

        static Reservation replay(AssetResponse replayResponse) {
            return new Reservation(null, replayResponse);
        }

        boolean isReplay() {
            return replayResponse != null;
        }
    }
}
