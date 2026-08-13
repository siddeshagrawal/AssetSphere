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

        record.complete(resourceId, 201, "{}", Instant.parse("2026-08-09T00:00:00Z"));

        assertThat(record.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(record.getResourceId()).isEqualTo(resourceId);
        assertThatThrownBy(() -> record.complete(UUID.randomUUID(), 201, "{}", Instant.parse("2026-08-09T00:00:00Z")))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void transitionsInProgressRecordToFailed() {
        IdempotencyRecord record = record();

        Instant failureExpiry = Instant.parse("2026-08-07T00:15:00Z");
        record.markFailed(failureExpiry);

        assertThat(record.getStatus()).isEqualTo(IdempotencyStatus.FAILED);
        assertThat(record.getExpiresAt()).isEqualTo(failureExpiry);
    }

    @Test
    void retriesFailedRecordWithFreshInProgressExpiry() {
        IdempotencyRecord record = record();
        record.markFailed(Instant.parse("2026-08-07T00:15:00Z"));
        Instant retryExpiry = Instant.parse("2026-08-07T00:30:00Z");

        record.retry(retryExpiry);

        assertThat(record.getStatus()).isEqualTo(IdempotencyStatus.IN_PROGRESS);
        assertThat(record.getExpiresAt()).isEqualTo(retryExpiry);
    }

    private IdempotencyRecord record() {
        return IdempotencyRecord.reserve(
                "key", UUID.randomUUID(), UUID.randomUUID(), "fingerprint", Instant.parse("2026-08-08T00:00:00Z")
        );
    }
}
