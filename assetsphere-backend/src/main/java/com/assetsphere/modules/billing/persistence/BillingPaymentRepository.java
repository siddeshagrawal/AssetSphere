package com.assetsphere.modules.billing.persistence;

import com.assetsphere.modules.billing.api.PaymentProvider;
import com.assetsphere.modules.billing.domain.BillingPayment;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface BillingPaymentRepository extends JpaRepository<BillingPayment, UUID> {
    Optional<BillingPayment> findByWorkspaceIdAndIdempotencyKey(UUID workspaceId, String idempotencyKey);
    Optional<BillingPayment> findByProviderAndProviderOrderId(PaymentProvider provider, String providerOrderId);
    Optional<BillingPayment> findByProviderAndProviderPaymentId(PaymentProvider provider, String providerPaymentId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from BillingPayment payment where payment.id = :id")
    Optional<BillingPayment> findLockedById(@Param("id") UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from BillingPayment payment where payment.provider = :provider and payment.providerOrderId = :orderId")
    Optional<BillingPayment> findLockedByProviderAndProviderOrderId(@Param("provider") PaymentProvider provider,
                                                                    @Param("orderId") String orderId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from BillingPayment payment where payment.provider = :provider and payment.providerPaymentId = :paymentId")
    Optional<BillingPayment> findLockedByProviderAndProviderPaymentId(@Param("provider") PaymentProvider provider,
                                                                      @Param("paymentId") String paymentId);
    Optional<BillingPayment> findFirstByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BillingPayment> findFirstByWorkspaceIdAndProviderAndStatusInAndCreatedAtAfterOrderByCreatedAtDesc(
            UUID workspaceId, PaymentProvider provider,
            Collection<com.assetsphere.modules.billing.api.PaymentStatus> statuses, Instant createdAfter);
    Optional<BillingPayment> findFirstByWorkspaceIdAndProviderAndStatusOrderByCreatedAtDesc(
            UUID workspaceId, PaymentProvider provider, com.assetsphere.modules.billing.api.PaymentStatus status);

    @Modifying
    @Query(value = """
            INSERT INTO billing_payments
                (id, workspace_id, user_id, requested_plan, provider, idempotency_key, receipt,
                 amount_minor, currency, status, created_at, updated_at, created_by, updated_by, version)
            VALUES (:id, :workspaceId, :userId, 'PRO', :provider, :idempotencyKey, :receipt,
                    :amountMinor, :currency, 'CREATED', now(), now(), :userId, :userId, 0)
            ON CONFLICT (workspace_id, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int createIfAbsent(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId,
                       @Param("userId") UUID userId, @Param("provider") String provider,
                       @Param("idempotencyKey") String idempotencyKey, @Param("receipt") String receipt,
                       @Param("amountMinor") long amountMinor, @Param("currency") String currency);
}
