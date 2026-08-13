package com.assetsphere.modules.asset.domain;

import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord extends BaseEntity {

    private static final String ASSET_UPLOAD_OPERATION = "ASSET_UPLOAD";

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "operation_type", nullable = false, length = 64)
    private String operationType;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IdempotencyStatus status;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecord() {
    }

    public static IdempotencyRecord reserve(
            String key,
            UUID userId,
            UUID workspaceId,
            String fingerprint,
            Instant expiresAt
    ) {
        return reserve(key, userId, workspaceId, ASSET_UPLOAD_OPERATION, fingerprint, expiresAt);
    }

    public static IdempotencyRecord reserve(
            String key, UUID userId, UUID workspaceId, String operationType,
            String fingerprint, Instant expiresAt
    ) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.idempotencyKey = requireText(key, "Idempotency key is required");
        record.userId = requireValue(userId, "User is required");
        record.workspaceId = requireValue(workspaceId, "Workspace is required");
        record.operationType = requireText(operationType, "Operation type is required");
        record.requestFingerprint = requireText(fingerprint, "Request fingerprint is required");
        record.status = IdempotencyStatus.IN_PROGRESS;
        record.expiresAt = requireValue(expiresAt, "Expiry time is required");
        return record;
    }

    public void complete(UUID resourceId, int responseStatus, String responseBody, Instant completedExpiry) {
        requireInProgress();
        this.resourceId = requireValue(resourceId, "Resource is required");
        if (responseStatus < 200 || responseStatus > 299) {
            throw new BusinessRuleViolationException("Response status must be successful");
        }
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.expiresAt = requireValue(completedExpiry, "Completion expiry is required");
        this.status = IdempotencyStatus.COMPLETED;
    }

    public void markFailed(Instant failedExpiry) {
        requireInProgress();
        this.status = IdempotencyStatus.FAILED;
        this.expiresAt = requireValue(failedExpiry, "Failure expiry is required");
    }

    public void retry(Instant inProgressExpiry) {
        if (status != IdempotencyStatus.FAILED) {
            throw new BusinessRuleViolationException("Only failed idempotency records can be retried");
        }
        this.resourceId = null;
        this.responseStatus = null;
        this.responseBody = null;
        this.expiresAt = requireValue(inProgressExpiry, "Retry expiry is required");
        this.status = IdempotencyStatus.IN_PROGRESS;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(requireValue(now, "Current time is required"));
    }

    private void requireInProgress() {
        if (status != IdempotencyStatus.IN_PROGRESS) {
            throw new BusinessRuleViolationException("Idempotency record is no longer in progress");
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolationException(message);
        }
        return value.trim();
    }

    private static <T> T requireValue(T value, String message) {
        if (value == null) {
            throw new BusinessRuleViolationException(message);
        }
        return value;
    }
}
