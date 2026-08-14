package com.assetsphere.modules.billing.persistence;

import com.assetsphere.modules.billing.api.PaymentProvider;
import java.sql.Timestamp;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BillingProviderEventRepository {
    private final JdbcTemplate jdbc;

    public boolean accept(PaymentProvider provider, String identity, Instant occurredAt,
                          int priority, String eventId) {
        return !jdbc.query("""
                INSERT INTO billing_provider_event_state
                    (provider, provider_identity, occurred_at, event_priority, provider_event_id)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (provider, provider_identity) DO UPDATE
                   SET occurred_at = EXCLUDED.occurred_at,
                       event_priority = EXCLUDED.event_priority,
                       provider_event_id = EXCLUDED.provider_event_id
                 WHERE EXCLUDED.occurred_at > billing_provider_event_state.occurred_at
                    OR (EXCLUDED.occurred_at = billing_provider_event_state.occurred_at
                        AND EXCLUDED.event_priority > billing_provider_event_state.event_priority)
                    OR (EXCLUDED.occurred_at = billing_provider_event_state.occurred_at
                        AND EXCLUDED.event_priority = billing_provider_event_state.event_priority
                        AND EXCLUDED.provider_event_id > billing_provider_event_state.provider_event_id)
                RETURNING 1
                """, (resultSet, rowNumber) -> resultSet.getInt(1), provider.name(), identity,
                Timestamp.from(occurredAt), priority, eventId).isEmpty();
    }
}
