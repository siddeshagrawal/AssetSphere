package com.assetsphere.infrastructure.kafka;

import java.util.UUID;

public record DltReplayResponse(String topic, int partition, long offset, String originalTopic, UUID eventId, String status) {}
