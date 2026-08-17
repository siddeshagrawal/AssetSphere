package com.assetsphere.modules.billing.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.assetsphere.modules.billing.api.PaymentProvider;
import com.assetsphere.modules.billing.api.UsageMetric;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@EnabledIfEnvironmentVariable(named = "ASSETSPHERE_POSTGRES_INTEGRATION", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BillingLifecycleRepositoryPostgresIntegrationTests {
    private JdbcTemplate jdbc;
    private BillingUsageRepository usage;
    private BillingProviderEventRepository providerEvents;

    @BeforeAll
    void setUp() {
        PGSimpleDataSource dataSource = BillingPostgresIntegrationDatabase.migratedDataSource();
        jdbc = new JdbcTemplate(dataSource);
        usage = new BillingUsageRepository(new NamedParameterJdbcTemplate(dataSource));
        providerEvents = new BillingProviderEventRepository(jdbc);
    }

    @Test
    void fullPeriodInstantSeparatesFreeUpgradeAndRenewalBuckets() {
        UUID workspaceId = UUID.randomUUID();
        String slug = "billing-period-" + workspaceId;
        Instant freeStart = Instant.parse("2026-08-14T00:00:00Z");
        Instant proStart = Instant.parse("2026-08-14T12:00:00Z");
        Instant renewalStart = Instant.parse("2026-09-14T12:00:00Z");
        jdbc.update("""
                INSERT INTO workspaces
                    (id, created_at, updated_at, version, name, slug, status, creator_user_id)
                VALUES (?, now(), now(), 0, 'Billing period test', ?, 'ACTIVE', ?)
                """, workspaceId, slug, UUID.randomUUID());
        try {
            assertThat(usage.incrementWithinLimit(workspaceId, UsageMetric.AI_INSIGHT, freeStart, 10)).isEqualTo(1);
            assertThat(usage.incrementWithinLimit(workspaceId, UsageMetric.AI_INSIGHT, proStart, 500)).isEqualTo(1);
            assertThat(usage.incrementWithinLimit(workspaceId, UsageMetric.AI_INSIGHT, proStart, 500)).isEqualTo(2);
            assertThat(usage.incrementWithinLimit(workspaceId, UsageMetric.AI_INSIGHT, renewalStart, 500)).isEqualTo(1);

            assertThat(usage.findUsage(workspaceId, freeStart).get(UsageMetric.AI_INSIGHT)).isEqualTo(1);
            assertThat(usage.findUsage(workspaceId, proStart).get(UsageMetric.AI_INSIGHT)).isEqualTo(2);
            assertThat(usage.findUsage(workspaceId, renewalStart).get(UsageMetric.AI_INSIGHT)).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM billing_usage_events WHERE workspace_id = ?", workspaceId);
            jdbc.update("DELETE FROM billing_usage WHERE workspace_id = ?", workspaceId);
            jdbc.update("DELETE FROM workspaces WHERE id = ?", workspaceId);
        }
    }

    @Test
    void providerOrderingRejectsOlderAndLowerPrioritySameTimestampEvents() {
        String identity = "SUBSCRIPTION:sub_test_" + UUID.randomUUID();
        Instant newer = Instant.parse("2026-08-14T12:00:01Z");
        try {
            assertThat(providerEvents.accept(PaymentProvider.STRIPE, identity, newer, 300, "evt_paid")).isTrue();
            assertThat(providerEvents.accept(PaymentProvider.STRIPE, identity,
                    newer.minusSeconds(1), 400, "evt_old_failed")).isFalse();
            assertThat(providerEvents.accept(PaymentProvider.STRIPE, identity,
                    newer, 200, "evt_same_active")).isFalse();
            assertThat(providerEvents.accept(PaymentProvider.STRIPE, identity,
                    newer, 500, "evt_same_deleted")).isTrue();
            assertThat(providerEvents.accept(PaymentProvider.STRIPE, identity,
                    newer, 350, "evt_same_late_active")).isFalse();
        } finally {
            jdbc.update("DELETE FROM billing_provider_event_state WHERE provider = ? AND provider_identity = ?",
                    PaymentProvider.STRIPE.name(), identity);
        }
    }

    @Test
    void subscriptionUpdatedWinsOverCreatedAtTheSameProviderTimestamp() {
        String identity = "SUBSCRIPTION:sub_test_" + UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-08-14T12:00:00Z");
        try {
            assertThat(providerEvents.accept(PaymentProvider.STRIPE, identity,
                    occurredAt, 340, "evt_created")).isTrue();
            assertThat(providerEvents.accept(PaymentProvider.STRIPE, identity,
                    occurredAt, 350, "evt_updated")).isTrue();
            assertThat(providerEvents.accept(PaymentProvider.STRIPE, identity,
                    occurredAt, 340, "evt_created_late")).isFalse();
        } finally {
            jdbc.update("DELETE FROM billing_provider_event_state WHERE provider = ? AND provider_identity = ?",
                    PaymentProvider.STRIPE.name(), identity);
        }
    }
}
