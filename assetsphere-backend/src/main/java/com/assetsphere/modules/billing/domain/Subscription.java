package com.assetsphere.modules.billing.domain;

import com.assetsphere.modules.billing.api.Plan;
import com.assetsphere.modules.billing.api.PaymentProvider;
import com.assetsphere.modules.billing.api.SubscriptionStatus;
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
@Table(name = "workspace_subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscription extends BaseEntity {
    @Column(name = "workspace_id", nullable = false, unique = true)
    private UUID workspaceId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Plan plan;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SubscriptionStatus status;
    @Column(name = "payment_provider", length = 32)
    private String paymentProvider;
    @Column(name = "external_subscription_id", length = 255)
    private String externalSubscriptionId;
    @Column(name = "current_period_start", nullable = false)
    private Instant currentPeriodStart;
    @Column(name = "usage_period_start", nullable = false)
    private Instant usagePeriodStart;
    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;
    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;

    public static Subscription free(UUID workspaceId, Instant periodStart, Instant periodEnd) {
        Subscription subscription = new Subscription();
        subscription.workspaceId = workspaceId;
        subscription.plan = Plan.FREE;
        subscription.status = SubscriptionStatus.ACTIVE;
        subscription.currentPeriodStart = periodStart;
        subscription.usagePeriodStart = periodStart;
        subscription.currentPeriodEnd = periodEnd;
        return subscription;
    }

    public void advancePeriod(Instant start, Instant end) {
        currentPeriodStart = start;
        usagePeriodStart = start;
        currentPeriodEnd = end;
        if (plan == Plan.FREE) status = SubscriptionStatus.ACTIVE;
        cancelAtPeriodEnd = false;
    }

    public void activatePro(String paymentProvider, String externalPaymentId, Instant start, Instant end) {
        if (paymentProvider == null || paymentProvider.isBlank()) {
            throw new IllegalArgumentException("Payment provider is required");
        }
        requireExternalIdentity(externalPaymentId);
        if (PaymentProvider.STRIPE.name().equals(paymentProvider) && !externalPaymentId.startsWith("sub_")) {
            throw new IllegalArgumentException("Stripe subscription identity is invalid");
        }
        if (hasExternalSubscriptionIdentity() && !externalSubscriptionId.equals(externalPaymentId)) {
            throw new IllegalStateException("External subscription identity cannot be replaced");
        }
        plan = Plan.PRO;
        status = SubscriptionStatus.ACTIVE;
        this.paymentProvider = paymentProvider;
        externalSubscriptionId = externalPaymentId;
        currentPeriodStart = start;
        usagePeriodStart = start;
        currentPeriodEnd = end;
        cancelAtPeriodEnd = false;
    }

    public void renew(Instant end) {
        currentPeriodStart = currentPeriodEnd;
        usagePeriodStart = currentPeriodStart;
        currentPeriodEnd = end;
        status = SubscriptionStatus.ACTIVE;
    }

    public void synchronizePeriod(Instant start, Instant end) {
        advanceUsagePeriodWhenNewer(start);
        if (start != null) currentPeriodStart = start;
        if (end != null) currentPeriodEnd = end;
        status = SubscriptionStatus.ACTIVE;
    }

    public void synchronizePeriodWithoutActivation(Instant start, Instant end) {
        advanceUsagePeriodWhenNewer(start);
        if (start != null) currentPeriodStart = start;
        if (end != null) currentPeriodEnd = end;
    }

    public void recoverMissingStripeSubscriptionId(String externalId) {
        if (plan != Plan.PRO || status != SubscriptionStatus.ACTIVE
                || !PaymentProvider.STRIPE.name().equals(paymentProvider)
                || hasExternalSubscriptionIdentity() || missingProviderIdentity(externalId)
                || !externalId.startsWith("sub_")) {
            throw new IllegalStateException("Only an active paid subscription with missing identity can be recovered");
        }
        externalSubscriptionId = externalId;
    }

    public void synchronizeCancellation(boolean cancelAtPeriodEnd) {
        this.cancelAtPeriodEnd = cancelAtPeriodEnd;
    }

    public void scheduleCancellation() { cancelAtPeriodEnd = true; }

    public void expireToFree(Instant start, Instant end) {
        plan = Plan.FREE;
        status = SubscriptionStatus.ACTIVE;
        paymentProvider = null;
        externalSubscriptionId = null;
        currentPeriodStart = start;
        usagePeriodStart = start;
        currentPeriodEnd = end;
        cancelAtPeriodEnd = false;
    }

    public boolean matches(String provider, String externalId) {
        return paymentProvider != null && paymentProvider.equals(provider)
                && hasExternalSubscriptionIdentity() && !missingProviderIdentity(externalId)
                && externalSubscriptionId.equals(externalId);
    }

    public boolean hasExternalSubscriptionIdentity() {
        return !missingProviderIdentity(externalSubscriptionId);
    }

    public void markPastDue() { status = SubscriptionStatus.PAST_DUE; }

    public void cancel(Instant freePeriodStart, Instant freePeriodEnd) {
        plan = Plan.FREE;
        status = SubscriptionStatus.ACTIVE;
        paymentProvider = null;
        externalSubscriptionId = null;
        currentPeriodStart = freePeriodStart;
        usagePeriodStart = freePeriodStart;
        currentPeriodEnd = freePeriodEnd;
        cancelAtPeriodEnd = false;
    }

    private void advanceUsagePeriodWhenNewer(Instant start) {
        if (start != null && !start.isBefore(currentPeriodEnd)) usagePeriodStart = start;
    }

    private void requireExternalIdentity(String value) {
        if (missingProviderIdentity(value)) throw new IllegalArgumentException("External subscription identity is required");
    }

    private boolean missingProviderIdentity(String value) {
        return value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim());
    }
}
