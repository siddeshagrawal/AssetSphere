package com.assetsphere.modules.billing.persistence;

import com.assetsphere.modules.billing.api.PaymentProvider;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BillingWebhookRepository {
    private final JdbcTemplate jdbc;

    public BillingWebhookRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean claim(PaymentProvider provider, String eventId, String eventType, String payloadHash, Instant receivedAt) {
        return jdbc.update("""
                INSERT INTO billing_webhook_events
                    (id, provider, provider_event_id, event_type, payload_hash, status, received_at)
                VALUES (?, ?, ?, ?, ?, 'RECEIVED', ?)
                ON CONFLICT (provider, provider_event_id) DO NOTHING
                """, UUID.randomUUID(), provider.name(), eventId, eventType, payloadHash,
                Timestamp.from(receivedAt)) == 1;
    }

    public void complete(PaymentProvider provider, String eventId, boolean processed, Instant completedAt) {
        jdbc.update("""
                UPDATE billing_webhook_events SET status = ?, processed_at = ?
                WHERE provider = ? AND provider_event_id = ?
                """, processed ? "PROCESSED" : "IGNORED", Timestamp.from(completedAt), provider.name(), eventId);
    }
}
