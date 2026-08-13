package com.assetsphere.modules.processing.outbox.application;

import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.processing.api.ProcessingProperties;
import com.assetsphere.modules.processing.outbox.domain.OutboxEvent;
import com.assetsphere.modules.processing.outbox.persistence.OutboxEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class OutboxPublishingStateService {

    private final OutboxEventRepository outboxEvents;
    private final ProcessingProperties properties;
    private final ClockProvider clockProvider;

    @Transactional
    public void markPublished(UUID eventId, String owner) {
        OutboxEvent event = claimedEvent(eventId, owner);
        event.markPublished(owner, clockProvider.now());
    }

    @Transactional
    public void recordFailure(UUID eventId, String owner, int claimedRetryCount, String error) {
        OutboxEvent event = claimedEvent(eventId, owner);
        Instant now = clockProvider.now();
        if (claimedRetryCount + 1 >= properties.getOutboxMaxRetries()) {
            event.markFailed(owner, now, error);
            return;
        }
        event.recordRetryableFailure(owner, now.plus(retryDelay(claimedRetryCount + 1)), error);
    }

    private OutboxEvent claimedEvent(UUID eventId, String owner) {
        return outboxEvents.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("Claimed outbox event no longer exists"));
    }

    private Duration retryDelay(int attempt) {
        long multiplier = 1L << Math.min(attempt - 1, 20);
        Duration candidate = properties.getOutboxInitialRetryDelay().multipliedBy(multiplier);
        return candidate.compareTo(properties.getOutboxMaxRetryDelay()) > 0
                ? properties.getOutboxMaxRetryDelay()
                : candidate;
    }
}
