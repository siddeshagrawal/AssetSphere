package com.assetsphere.modules.processing.api;

import java.util.UUID;

/** Technical delivery port used only after a durable outbox event has been claimed. */
public interface OutboxMessagePublisher {

    void publish(UUID eventId, UUID aggregateId, String topic, String payload);
}
