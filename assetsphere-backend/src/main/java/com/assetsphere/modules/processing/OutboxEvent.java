package com.assetsphere.modules.processing;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

import com.assetsphere.modules.common.BaseEntity;

@Getter
@Entity
@Table(name = "outbox_events")
public class OutboxEvent extends BaseEntity {
    @Column(name = "aggregate_type", nullable = false) private String aggregateType;
    @Column(name = "aggregate_id", nullable = false) private UUID aggregateId;
    @Column(name = "event_type", nullable = false) private String eventType;
    @Column(name = "event_version", nullable = false) private int eventVersion;
    @Column(nullable = false) private String topic;
    @Column(nullable = false) private String payload;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private OutboxStatus status;
    @Column(name = "retry_count", nullable = false) private int retryCount;
    @Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "last_error") private String lastError;

    protected OutboxEvent() { }

    public static OutboxEvent createPending(UUID aggregateId, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.aggregateType = "ASSET";
        event.aggregateId = aggregateId;
        event.eventType = "asset.uploaded.v1";
        event.eventVersion = 1;
        event.topic = "assetsphere.asset.asset-uploaded.v1";
        event.payload = payload;
        event.status = OutboxStatus.PENDING;
        event.nextAttemptAt = Instant.now();
        return event;
    }

    public void markProcessing() {
        if (status != OutboxStatus.PENDING) throw new IllegalStateException("Only pending events can be claimed");
        status = OutboxStatus.PROCESSING;
    }

    public void markPublished(Instant publishedAt) {
        if (status != OutboxStatus.PROCESSING) throw new IllegalStateException("Only claimed events can be published");
        status = OutboxStatus.PUBLISHED;
        this.publishedAt = publishedAt;
    }

    public void recordFailure(Instant nextAttemptAt, String error) {
        if (status != OutboxStatus.PROCESSING) throw new IllegalStateException("Only claimed events can fail");
        retryCount++;
        this.nextAttemptAt = nextAttemptAt;
        lastError = error;
        status = OutboxStatus.PENDING;
    }
}
