package com.assetsphere.modules.billing.domain;

import com.assetsphere.modules.billing.api.PaymentProvider;
import com.assetsphere.modules.billing.api.PaymentStatus;
import com.assetsphere.modules.billing.api.Plan;
import com.assetsphere.modules.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "billing_payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingPayment extends BaseEntity {
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(name = "requested_plan", nullable = false, length = 16)
    private Plan requestedPlan;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentProvider provider;
    @Column(name = "provider_order_id")
    private String providerOrderId;
    @Column(name = "provider_checkout_url", length = 2048)
    private String providerCheckoutUrl;
    @Column(name = "provider_payment_id")
    private String providerPaymentId;
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;
    @Column(nullable = false, length = 64)
    private String receipt;
    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;
    @Column(nullable = false, length = 8)
    private String currency;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;
    @Column(name = "failure_code", length = 128)
    private String failureCode;
    @Column(name = "verified_at")
    private Instant verifiedAt;

    public static BillingPayment create(UUID workspaceId, UUID userId, Plan plan, PaymentProvider provider,
                                        String idempotencyKey, String receipt, long amountMinor, String currency) {
        BillingPayment payment = new BillingPayment();
        payment.workspaceId = workspaceId;
        payment.userId = userId;
        payment.requestedPlan = plan;
        payment.provider = provider;
        payment.idempotencyKey = idempotencyKey;
        payment.receipt = receipt;
        payment.amountMinor = amountMinor;
        payment.currency = currency;
        payment.status = PaymentStatus.CREATED;
        return payment;
    }

    public void orderCreated(String orderId, String checkoutUrl) {
        providerOrderId = orderId;
        providerCheckoutUrl = checkoutUrl;
        status = PaymentStatus.ORDER_CREATED;
        failureCode = null;
    }

    public void paid(String paymentId, Instant verifiedAt) {
        requireProviderIdentity(paymentId);
        if (hasProviderPaymentIdentity() && !providerPaymentId.equals(paymentId)) {
            throw new IllegalStateException("Provider payment identity cannot be replaced");
        }
        providerPaymentId = paymentId;
        this.verifiedAt = verifiedAt;
        status = PaymentStatus.PAID;
        failureCode = null;
    }

    public void recoverPaidProviderIdentity(String paymentId) {
        if (provider != PaymentProvider.STRIPE || status != PaymentStatus.PAID || hasProviderPaymentIdentity()
                || paymentId == null || !paymentId.startsWith("sub_")) {
            throw new IllegalStateException("Only a paid payment with missing provider identity can be recovered");
        }
        providerPaymentId = paymentId;
    }

    public void providerPaymentCreated(String paymentId) {
        requireProviderIdentity(paymentId);
        if (!hasProviderPaymentIdentity()) providerPaymentId = paymentId;
        else if (!providerPaymentId.equals(paymentId)) {
            throw new IllegalStateException("Provider payment identity cannot be replaced");
        }
    }

    public boolean hasProviderPaymentIdentity() {
        return !missingProviderIdentity(providerPaymentId);
    }

    public void canceled(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
        status = PaymentStatus.CANCELED;
        failureCode = null;
    }

    public void failed(String failureCode, Instant verifiedAt) {
        this.failureCode = failureCode;
        this.verifiedAt = verifiedAt;
        status = PaymentStatus.FAILED;
    }

    private void requireProviderIdentity(String value) {
        if (missingProviderIdentity(value)) throw new IllegalArgumentException("Provider payment identity is required");
        if (provider == PaymentProvider.STRIPE && !value.startsWith("sub_")) {
            throw new IllegalArgumentException("Stripe subscription identity is invalid");
        }
    }

    private boolean missingProviderIdentity(String value) {
        return value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim());
    }
}
