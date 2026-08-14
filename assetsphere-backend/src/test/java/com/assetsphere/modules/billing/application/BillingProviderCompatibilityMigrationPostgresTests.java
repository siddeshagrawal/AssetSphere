package com.assetsphere.modules.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetsphere.modules.billing.api.PaymentProvider;
import com.assetsphere.modules.billing.persistence.BillingPostgresIntegrationDatabase;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@EnabledIfEnvironmentVariable(named = "ASSETSPHERE_POSTGRES_INTEGRATION", matches = "true")
class BillingProviderCompatibilityMigrationPostgresTests {
    @Test
    void legacyProviderMigratesAndCurrentProviderAndStatusContractsPersist() {
        Assumptions.assumeTrue(configured("ASSETSPHERE_POSTGRES_HOST") && configured("POSTGRES_PASSWORD"),
                "external PostgreSQL integration environment not configured");
        PGSimpleDataSource adminDataSource = BillingPostgresIntegrationDatabase.dataSource();
        String schema = "billing_v16_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate adminJdbc = new JdbcTemplate(adminDataSource);
        try {
            adminJdbc.execute("CREATE SCHEMA " + schema);
            PGSimpleDataSource schemaDataSource = BillingPostgresIntegrationDatabase.dataSource();
            schemaDataSource.setCurrentSchema(schema + ",public");
            JdbcTemplate jdbc = new JdbcTemplate(schemaDataSource);
            BillingPostgresIntegrationDatabase.migrate(schemaDataSource, schema, "15");
            UUID workspaceId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO workspaces(id, created_at, updated_at, version, name, slug, status, creator_user_id)
                    VALUES (?, now(), now(), 0, 'Migration test', ?, 'ACTIVE', ?)
                    """, workspaceId, "migration-" + workspaceId, userId);
            jdbc.update("""
                    INSERT INTO workspace_subscriptions(id, workspace_id, plan, status, current_period_start,
                        current_period_end, created_at, updated_at, version)
                    VALUES (?, ?, 'FREE', 'ACTIVE', now(), now() + interval '1 month', now(), now(), 0)
                    """, UUID.randomUUID(), workspaceId);
            UUID paymentId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO billing_payments(id, workspace_id, user_id, requested_plan, provider,
                        idempotency_key, receipt, amount_minor, currency, status, created_at, updated_at, version)
                    VALUES (?, ?, ?, 'PRO', 'RAZORPAY', ?, ?, 99900, 'INR', 'FAILED', now(), now(), 0)
                    """, paymentId, workspaceId, userId, "key-" + paymentId, "receipt-" + paymentId);

            BillingPostgresIntegrationDatabase.migrate(schemaDataSource, schema, null);

            String provider = jdbc.queryForObject("SELECT provider FROM billing_payments WHERE id = ?",
                    String.class, paymentId);
            assertThat(PaymentProvider.valueOf(provider)).isEqualTo(PaymentProvider.RAZORPAY_LOCAL);
            assertThat(jdbc.update("UPDATE billing_payments SET status = 'CANCELED' WHERE id = ?", paymentId))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT payment_provider IS NULL FROM workspace_subscriptions WHERE workspace_id = ?",
                    Boolean.class, workspaceId)).isTrue();
            assertThatThrownBy(() -> jdbc.update("UPDATE billing_payments SET provider = 'UNKNOWN' WHERE id = ?", paymentId))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            adminJdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private boolean configured(String name) {
        String value = System.getenv(name);
        return value != null && !value.isBlank();
    }
}
