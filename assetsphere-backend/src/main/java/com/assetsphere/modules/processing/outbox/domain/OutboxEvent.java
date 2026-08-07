package com.assetsphere.modules.processing.outbox.domain;

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
@Table(name = "outbox_events")
public class OutboxEvent extends BaseEntity {

    private static final int MAX_LAST_ERROR_LENGTH = 1000;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private int eventVersion;

    @Column(nullable = false, length = 255)
    private String topic;

    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error", length = MAX_LAST_ERROR_LENGTH)
    private String lastError;

    protected OutboxEvent() {
    }

    public static OutboxEvent createPending(UUID aggregateId, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.aggregateType = "ASSET";
        event.aggregateId = requireValue(aggregateId, "Aggregate is required");
        event.eventType = "asset.uploaded.v1";
        event.eventVersion = 1;
        event.topic = "assetsphere.asset.asset-uploaded.v1";
        event.payload = requireText(payload, "Payload is required");
        event.status = OutboxStatus.PENDING;
        event.nextAttemptAt = Instant.now();
        return event;
    }

    public void markProcessing() {
        requireStatus(OutboxStatus.PENDING, "Only pending events can be claimed");
        status = OutboxStatus.PROCESSING;
    }

    public void markPublished(Instant publishedAt) {
        requireStatus(OutboxStatus.PROCESSING, "Only processing events can be published");
        status = OutboxStatus.PUBLISHED;
        this.publishedAt = requireValue(publishedAt, "Published time is required");
        this.lastError = null;
        // nextAttemptAt remains non-null because V4 defines it as NOT NULL.
    }

    public void recordRetryableFailure(Instant nextAttemptAt, String error) {
        requireStatus(OutboxStatus.PROCESSING, "Only processing events can fail");
        retryCount++;
        this.nextAttemptAt = requireValue(nextAttemptAt, "Next attempt time is required");
        this.lastError = boundError(error);
        status = OutboxStatus.PENDING;
    }

    public void markFailed(Instant failedAt, String error) {
        if (status != OutboxStatus.PENDING && status != OutboxStatus.PROCESSING) {
            throw new BusinessRuleViolationException("Only pending or processing events can fail permanently");
        }
        status = OutboxStatus.FAILED;
        nextAttemptAt = requireValue(failedAt, "Failure time is required");
        lastError = boundError(error);
    }

    private void requireStatus(OutboxStatus expected, String message) {
        if (status != expected) {
            throw new BusinessRuleViolationException(message);
        }
    }

    private String boundError(String error) {
        if (error == null || error.isBlank()) {
            return null;
        }
        String normalized = error.strip();
        return normalized.length() <= MAX_LAST_ERROR_LENGTH
                ? normalized
                : normalized.substring(0, MAX_LAST_ERROR_LENGTH);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolationException(message);
        }
        return value;
    }

    private static <T> T requireValue(T value, String message) {
        if (value == null) {
            throw new BusinessRuleViolationException(message);
        }
        return value;
    }
}
