package com.assetsphere.infrastructure.kafka;

import java.util.UUID;

class KafkaPublicationException extends RuntimeException {

    KafkaPublicationException(UUID eventId, Throwable cause) {
        super("Kafka delivery failed for outbox event " + eventId, cause);
    }
}
