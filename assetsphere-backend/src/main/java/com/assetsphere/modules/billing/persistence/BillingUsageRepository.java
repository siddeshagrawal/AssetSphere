package com.assetsphere.modules.billing.persistence;

import com.assetsphere.modules.billing.api.UsageMetric;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BillingUsageRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public long incrementWithinLimit(UUID workspaceId, UsageMetric metric, LocalDate periodStart, long limit) {
        var parameters = new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID()).addValue("workspaceId", workspaceId)
                .addValue("metric", metric.name()).addValue("periodStart", periodStart).addValue("limit", limit);
        return jdbc.query("""
                INSERT INTO billing_usage (id, workspace_id, metric, period_start, usage_count, created_at, updated_at)
                VALUES (:id, :workspaceId, :metric, :periodStart, 1, now(), now())
                ON CONFLICT (workspace_id, metric, period_start) DO UPDATE
                   SET usage_count = billing_usage.usage_count + 1, updated_at = now()
                 WHERE billing_usage.usage_count < :limit
                RETURNING usage_count
                """, parameters, (resultSet, row) -> resultSet.getLong(1)).stream().findFirst().orElse(-1L);
    }

    public Map<UsageMetric, Long> findUsage(UUID workspaceId, LocalDate periodStart) {
        Map<UsageMetric, Long> usage = new EnumMap<>(UsageMetric.class);
        jdbc.query("SELECT metric, usage_count FROM billing_usage WHERE workspace_id=:workspaceId AND period_start=:periodStart",
                new MapSqlParameterSource().addValue("workspaceId", workspaceId).addValue("periodStart", periodStart),
                resultSet -> { usage.put(UsageMetric.valueOf(resultSet.getString(1)), resultSet.getLong(2)); });
        return usage;
    }

    public long incrementOnceWithinLimit(UUID workspaceId, UsageMetric metric, UUID operationId,
                                         LocalDate periodStart, long limit) {
        var parameters = new MapSqlParameterSource()
                .addValue("eventId", UUID.randomUUID()).addValue("usageId", UUID.randomUUID())
                .addValue("workspaceId", workspaceId).addValue("metric", metric.name())
                .addValue("operationId", operationId).addValue("periodStart", periodStart).addValue("limit", limit);
        return jdbc.queryForObject("""
                WITH claimed AS (
                    INSERT INTO billing_usage_events (id, workspace_id, metric, operation_id, period_start, created_at)
                    VALUES (:eventId, :workspaceId, :metric, :operationId, :periodStart, now())
                    ON CONFLICT (workspace_id, metric, operation_id) DO NOTHING
                    RETURNING 1
                ), consumed AS (
                    INSERT INTO billing_usage (id, workspace_id, metric, period_start, usage_count, created_at, updated_at)
                    SELECT :usageId, :workspaceId, :metric, :periodStart, 1, now(), now() FROM claimed
                    ON CONFLICT (workspace_id, metric, period_start) DO UPDATE
                       SET usage_count = billing_usage.usage_count + 1, updated_at = now()
                     WHERE billing_usage.usage_count < :limit
                    RETURNING usage_count
                )
                SELECT COALESCE((SELECT usage_count FROM consumed),
                       CASE WHEN EXISTS (SELECT 1 FROM claimed) THEN -1 ELSE 0 END)
                """, parameters, Long.class);
    }
}
