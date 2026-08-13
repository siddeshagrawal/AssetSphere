package com.assetsphere.modules.processing.outbox.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL is the coordination boundary: rows are locked and leased atomically with SKIP LOCKED. */
@Repository
@RequiredArgsConstructor
public class OutboxEventClaimRepository {

    private static final String CLAIM_SQL = """
            WITH candidates AS (
                SELECT id
                FROM outbox_events
                WHERE (status = 'PENDING' AND next_attempt_at <= :now)
                   OR (status = 'PROCESSING' AND claimed_at <= :staleBefore)
                ORDER BY created_at, id
                FOR UPDATE SKIP LOCKED
                LIMIT :batchSize
            )
            UPDATE outbox_events event
               SET status = 'PROCESSING',
                   claim_owner = :owner,
                   claimed_at = :now,
                   updated_at = :now,
                   version = version + 1
              FROM candidates
             WHERE event.id = candidates.id
            RETURNING event.id, event.aggregate_id, event.topic, event.payload, event.retry_count
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public List<OutboxClaim> claimAvailable(String owner, Instant now, Instant staleBefore, int batchSize) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("owner", owner)
                .addValue("now", now.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("staleBefore", staleBefore.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("batchSize", batchSize);
        return jdbcTemplate.query(CLAIM_SQL, parameters, this::map);
    }

    private OutboxClaim map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new OutboxClaim(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("aggregate_id", UUID.class),
                resultSet.getString("topic"),
                resultSet.getString("payload"),
                resultSet.getInt("retry_count")
        );
    }
}
