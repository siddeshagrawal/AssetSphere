package com.assetsphere.modules.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetsphere.modules.billing.api.PaymentProvider;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class BillingProviderCompatibilityMigrationPostgresTests {
    @Test
    void legacyProviderMigratesAndCurrentProviderAndStatusContractsPersist() {
        Assumptions.assumeTrue(configured("ASSETSPHERE_POSTGRES_HOST") && configured("POSTGRES_PASSWORD"),
                "external PostgreSQL integration environment not configured");
        PGSimpleDataSource dataSource = dataSource();
        String schema = "billing_v16_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.execute("CREATE SCHEMA " + schema);
            migrate(dataSource, schema, "15");
            jdbc.execute("SET search_path TO " + schema + ", public");
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

            migrate(dataSource, schema, null);
            jdbc.execute("SET search_path TO " + schema + ", public");

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
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private void migrate(PGSimpleDataSource dataSource, String schema, String target) {
        var configuration = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration");
        if (target != null) configuration.target(target);
        configuration.load().migrate();
    }

    private PGSimpleDataSource dataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl("jdbc:postgresql://%s:5432/assetsphere".formatted(required("ASSETSPHERE_POSTGRES_HOST")));
        dataSource.setUser("assetsphere");
        dataSource.setPassword(required("POSTGRES_PASSWORD"));
        return dataSource;
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }

    private boolean configured(String name) {
        String value = System.getenv(name);
        return value != null && !value.isBlank();
    }
}
