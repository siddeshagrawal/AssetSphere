package com.assetsphere.modules.processing.outbox.domain;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    PUBLISHED,
    FAILED
}
