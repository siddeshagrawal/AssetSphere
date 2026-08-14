package com.assetsphere.modules.billing.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.assetsphere.modules.billing.api.PaymentProvider;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

@EnabledIfEnvironmentVariable(named = "ASSETSPHERE_POSTGRES_INTEGRATION", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BillingWebhookRepositoryPostgresIntegrationTests {
    private JdbcTemplate jdbc;
    private BillingWebhookRepository repository;

    @BeforeAll
    void setUp() {
        PGSimpleDataSource dataSource = BillingPostgresIntegrationDatabase.migratedDataSource();
        jdbc = new JdbcTemplate(dataSource);
        repository = new BillingWebhookRepository(jdbc);
    }

    @Test
    void claimPersistsTimestampAndDuplicateProviderEventRemainsIdempotent() {
        String eventId = "repository-test-" + UUID.randomUUID();
        Instant receivedAt = Instant.parse("2026-08-13T05:45:30.123Z");
        Instant completedAt = receivedAt.plusSeconds(5);
        try {
            assertThat(repository.claim(PaymentProvider.STRIPE, eventId, "checkout.session.completed",
                    "a".repeat(64), receivedAt)).isTrue();
            assertThat(repository.claim(PaymentProvider.STRIPE, eventId, "checkout.session.completed",
                    "a".repeat(64), receivedAt.plusSeconds(1))).isFalse();

            OffsetDateTime persistedReceivedAt = jdbc.queryForObject("""
                    SELECT received_at FROM billing_webhook_events
                    WHERE provider = ? AND provider_event_id = ?
                    """, OffsetDateTime.class, PaymentProvider.STRIPE.name(), eventId);
            assertThat(persistedReceivedAt.toInstant()).isEqualTo(receivedAt);

            repository.complete(PaymentProvider.STRIPE, eventId, true, completedAt);
            OffsetDateTime persistedCompletedAt = jdbc.queryForObject("""
                    SELECT processed_at FROM billing_webhook_events
                    WHERE provider = ? AND provider_event_id = ?
                    """, OffsetDateTime.class, PaymentProvider.STRIPE.name(), eventId);
            assertThat(persistedCompletedAt.toInstant()).isEqualTo(completedAt);
        } finally {
            jdbc.update("DELETE FROM billing_webhook_events WHERE provider = ? AND provider_event_id = ?",
                    PaymentProvider.STRIPE.name(), eventId);
        }
    }
}
