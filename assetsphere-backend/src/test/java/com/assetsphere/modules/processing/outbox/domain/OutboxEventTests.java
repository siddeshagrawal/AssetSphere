package com.assetsphere.modules.processing.outbox.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxEventTests {

    @Test
    void createsPendingEvent() {
        OutboxEvent event = event();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getRetryCount()).isZero();
        assertThat(event.getNextAttemptAt()).isNotNull();
    }

    @Test
    void transitionsProcessingEventToPublishedAndClearsError() {
        OutboxEvent event = event();
        event.markProcessing();
        event.recordRetryableFailure(Instant.parse("2026-08-07T01:00:00Z"), "temporary failure");
        event.markProcessing();

        event.markPublished(Instant.parse("2026-08-07T02:00:00Z"));

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getLastError()).isNull();
        assertThat(event.getPublishedAt()).isEqualTo(Instant.parse("2026-08-07T02:00:00Z"));
    }

    @Test
    void retryableFailureReturnsEventToPendingAndIncrementsRetryCount() {
        OutboxEvent event = event();
        Instant nextAttempt = Instant.parse("2026-08-07T01:00:00Z");
        event.markProcessing();

        event.recordRetryableFailure(nextAttempt, "temporary failure");

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isEqualTo(nextAttempt);
        assertThat(event.getLastError()).isEqualTo("temporary failure");
    }

    @Test
    void boundsLastErrorAndSupportsPermanentFailure() {
        OutboxEvent event = event();
        String error = "x".repeat(1001);

        event.markFailed(Instant.parse("2026-08-07T01:00:00Z"), error);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getLastError()).hasSize(1000);
    }

    @Test
    void rejectsInvalidTransitions() {
        OutboxEvent event = event();

        assertThatThrownBy(() -> event.markPublished(Instant.now()))
                .isInstanceOf(BusinessRuleViolationException.class);
        event.markProcessing();
        event.markPublished(Instant.now());
        assertThatThrownBy(event::markProcessing)
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> event.markFailed(Instant.now(), "failure"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    private OutboxEvent event() {
        return OutboxEvent.createPending(UUID.randomUUID(), "{}");
    }
}
