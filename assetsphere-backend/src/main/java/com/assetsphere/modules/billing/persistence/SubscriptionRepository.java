package com.assetsphere.modules.billing.persistence;

import com.assetsphere.modules.billing.domain.Subscription;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    Optional<Subscription> findByWorkspaceId(UUID workspaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select subscription from Subscription subscription where subscription.workspaceId = :workspaceId")
    Optional<Subscription> findLockedByWorkspaceId(@Param("workspaceId") UUID workspaceId);

    @Modifying
    @Query(value = """
            INSERT INTO workspace_subscriptions
                (id, workspace_id, plan, status, current_period_start, current_period_end,
                 created_at, updated_at, created_by, updated_by, version)
            VALUES (:id, :workspaceId, 'FREE', 'ACTIVE', :periodStart, :periodEnd,
                    now(), now(), :actorId, :actorId, 0)
            ON CONFLICT (workspace_id) DO NOTHING
            """, nativeQuery = true)
    void createFreeIfAbsent(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId,
                            @Param("periodStart") Instant periodStart, @Param("periodEnd") Instant periodEnd,
                            @Param("actorId") UUID actorId);
}
