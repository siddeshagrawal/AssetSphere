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
        IdempotencyRecord record = new IdempotencyRecord();
        record.idempotencyKey = requireText(key, "Idempotency key is required");
        record.userId = requireValue(userId, "User is required");
        record.workspaceId = requireValue(workspaceId, "Workspace is required");
        record.operationType = ASSET_UPLOAD_OPERATION;
        record.requestFingerprint = requireText(fingerprint, "Request fingerprint is required");
        record.status = IdempotencyStatus.IN_PROGRESS;
        record.expiresAt = requireValue(expiresAt, "Expiry time is required");
        return record;
    }

    public void complete(UUID resourceId, int responseStatus, String responseBody) {
        requireInProgress();
        this.resourceId = requireValue(resourceId, "Resource is required");
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.status = IdempotencyStatus.COMPLETED;
    }

    public void markFailed() {
        requireInProgress();
        this.status = IdempotencyStatus.FAILED;
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
