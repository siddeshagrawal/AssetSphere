package com.assetsphere.modules.processing.consumer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "processed_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "consumer_name", nullable = false, length = 128)
    private String consumerName;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public static ProcessedEvent processed(UUID eventId, String consumerName, Instant processedAt) {
        ProcessedEvent event = new ProcessedEvent();
        event.eventId = eventId;
        event.consumerName = consumerName;
        event.processedAt = processedAt;
        return event;
    }
}
