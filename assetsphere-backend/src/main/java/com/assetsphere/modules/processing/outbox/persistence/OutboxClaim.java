package com.assetsphere.modules.processing.outbox.persistence;

import java.util.UUID;

public record OutboxClaim(UUID id, UUID aggregateId, String topic, String payload, int retryCount) {
}
