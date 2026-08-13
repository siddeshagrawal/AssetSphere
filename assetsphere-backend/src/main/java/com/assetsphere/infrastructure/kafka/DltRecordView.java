package com.assetsphere.infrastructure.kafka;

import java.time.Instant;
import java.util.UUID;

public record DltRecordView(
        String topic,
        int partition,
        long offset,
        String originalTopic,
        UUID eventId,
        UUID workspaceId,
        UUID assetId,
        UUID assetVersionId,
        String failureReason,
        Integer retryCount,
        Instant failedAt
) {}
