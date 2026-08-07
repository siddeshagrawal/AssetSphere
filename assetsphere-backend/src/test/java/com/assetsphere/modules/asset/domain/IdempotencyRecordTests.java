package com.assetsphere.modules.asset.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdempotencyRecordTests {

    @Test
    void reservesThenCompletesExactlyOnce() {
        IdempotencyRecord record = record();
        UUID resourceId = UUID.randomUUID();

        record.complete(resourceId, 201, "{}" );

        assertThat(record.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(record.getResourceId()).isEqualTo(resourceId);
        assertThatThrownBy(() -> record.complete(UUID.randomUUID(), 201, "{}"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void transitionsInProgressRecordToFailed() {
        IdempotencyRecord record = record();

        record.markFailed();

        assertThat(record.getStatus()).isEqualTo(IdempotencyStatus.FAILED);
    }

    private IdempotencyRecord record() {
        return IdempotencyRecord.reserve(
                "key", UUID.randomUUID(), UUID.randomUUID(), "fingerprint", Instant.parse("2026-08-08T00:00:00Z")
        );
    }
}
