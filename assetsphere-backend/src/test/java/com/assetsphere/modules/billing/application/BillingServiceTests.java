package com.assetsphere.modules.billing.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import com.assetsphere.modules.billing.api.BillingProperties;
import com.assetsphere.modules.billing.api.UsageMetric;
import com.assetsphere.modules.billing.api.Plan;
import com.assetsphere.modules.billing.api.WorkspaceResourceUsageProvider;
import com.assetsphere.modules.billing.domain.Subscription;
import com.assetsphere.modules.billing.persistence.BillingUsageRepository;
import com.assetsphere.modules.billing.persistence.BillingPaymentRepository;
import com.assetsphere.modules.billing.persistence.SubscriptionRepository;
import com.assetsphere.modules.billing.domain.BillingPayment;
import com.assetsphere.modules.billing.api.PaymentProvider;
import com.assetsphere.modules.billing.api.PaymentStatus;
import com.assetsphere.modules.common.exception.QuotaExceededException;
import com.assetsphere.modules.common.time.ClockProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class BillingServiceTests {

    @Test
    void centralizesIncreasingThreePlanEntitlements() {
        BillingProperties properties = new BillingProperties();
        org.assertj.core.api.Assertions.assertThat(properties.entitlements(Plan.PRO).maxAssets())
                .isGreaterThan(properties.entitlements(Plan.FREE).maxAssets());
        org.assertj.core.api.Assertions.assertThat(properties.entitlements(Plan.ENTERPRISE).maxAssets())
                .isGreaterThan(properties.entitlements(Plan.PRO).maxAssets());
        org.assertj.core.api.Assertions.assertThat(properties.entitlements(Plan.ENTERPRISE).fullAuditEnabled()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void consumesMonthlyUsageAndRejectsTheNextRequestAtTheLimit() {
        UUID workspaceId = UUID.randomUUID();
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        BillingUsageRepository usage = mock(BillingUsageRepository.class);
        WorkspaceResourceUsageProvider resources = mock(WorkspaceResourceUsageProvider.class);
        ObjectProvider<WorkspaceResourceUsageProvider> providers = mock(ObjectProvider.class);
        Instant now = Instant.parse("2026-08-11T00:00:00Z");
        when(subscriptions.findByWorkspaceId(workspaceId)).thenReturn(Optional.of(
                Subscription.free(workspaceId, Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"))));
        when(providers.orderedStream()).thenReturn(Stream.of(resources));
        when(usage.incrementWithinLimit(workspaceId, UsageMetric.ASK, LocalDate.parse("2026-08-01"), 50))
                .thenReturn(50L, -1L);
        BillingService billing = new BillingService(subscriptions, usage, mock(BillingPaymentRepository.class), new BillingProperties(), new BillingPaymentProperties(),
                (ClockProvider) () -> now, providers, mock(ObjectProvider.class));

        billing.consume(workspaceId, UsageMetric.ASK);
        assertThatThrownBy(() -> billing.consume(workspaceId, UsageMetric.ASK))
                .isInstanceOf(QuotaExceededException.class)
                .hasMessage("You've used 50/50 Ask requests this month.");
        verify(usage, times(2)).incrementWithinLimit(
                workspaceId, UsageMetric.ASK, LocalDate.parse("2026-08-01"), 50);
    }

    @Test
    @SuppressWarnings("unchecked")
    void localActivationUsesOneMonthFromConfirmationTime() {
        UUID workspaceId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-12T10:15:30Z");
        Subscription subscription = Subscription.free(workspaceId,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"));
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        when(subscriptions.findByWorkspaceId(workspaceId)).thenReturn(Optional.of(subscription));
        when(subscriptions.findLockedByWorkspaceId(workspaceId)).thenReturn(Optional.of(subscription));
        BillingService billing = new BillingService(subscriptions, mock(BillingUsageRepository.class),
                mock(BillingPaymentRepository.class), new BillingProperties(), new BillingPaymentProperties(), () -> now,
                mock(ObjectProvider.class), mock(ObjectProvider.class));

        billing.activatePro(workspaceId, com.assetsphere.modules.billing.api.PaymentProvider.RAZORPAY_LOCAL, "payment_1");

        org.assertj.core.api.Assertions.assertThat(subscription.getCurrentPeriodStart()).isEqualTo(now);
        org.assertj.core.api.Assertions.assertThat(subscription.getCurrentPeriodEnd())
                .isEqualTo(Instant.parse("2026-09-12T10:15:30Z"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void repairsOnlyMissingStripeSubscriptionIdentityAndKeepsAuthoritativePeriod() {
        UUID workspaceId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-13T00:00:00Z");
        Instant originalStart = Instant.parse("2026-08-01T00:00:00Z");
        Instant originalEnd = Instant.parse("2026-09-01T00:00:00Z");
        Instant stripeStart = Instant.parse("2026-08-12T00:00:00Z");
        Instant stripeEnd = Instant.parse("2026-09-12T00:00:00Z");
        Subscription subscription = Subscription.free(workspaceId, originalStart, originalEnd);
        subscription.activatePro(PaymentProvider.STRIPE.name(), "sub_original", originalStart, originalEnd);
        org.springframework.test.util.ReflectionTestUtils.setField(subscription, "externalSubscriptionId", null);
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        when(subscriptions.findByWorkspaceId(workspaceId)).thenReturn(Optional.of(subscription));
        when(subscriptions.findLockedByWorkspaceId(workspaceId)).thenReturn(Optional.of(subscription));
        BillingService billing = new BillingService(subscriptions, mock(BillingUsageRepository.class),
                mock(BillingPaymentRepository.class), new BillingProperties(), new BillingPaymentProperties(),
                () -> now, mock(ObjectProvider.class), mock(ObjectProvider.class));

        billing.recoverStripeSubscriptionIdentity(workspaceId, "sub_123", stripeStart, stripeEnd);

        org.assertj.core.api.Assertions.assertThat(subscription.getPlan()).isEqualTo(Plan.PRO);
        org.assertj.core.api.Assertions.assertThat(subscription.getStatus())
                .isEqualTo(com.assetsphere.modules.billing.api.SubscriptionStatus.ACTIVE);
        org.assertj.core.api.Assertions.assertThat(subscription.getExternalSubscriptionId()).isEqualTo("sub_123");
        org.assertj.core.api.Assertions.assertThat(subscription.getCurrentPeriodStart()).isEqualTo(stripeStart);
        org.assertj.core.api.Assertions.assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(stripeEnd);

        billing.recoverStripeSubscriptionIdentity(workspaceId, "sub_123", stripeStart, stripeEnd);
        assertThatThrownBy(() -> billing.recoverStripeSubscriptionIdentity(
                workspaceId, "sub_456", stripeStart, stripeEnd))
                .isInstanceOf(com.assetsphere.modules.common.exception.ConflictException.class);
        org.assertj.core.api.Assertions.assertThat(subscription.getExternalSubscriptionId()).isEqualTo("sub_123");
    }

    @Test
    @SuppressWarnings("unchecked")
    void freshFreeWorkspaceStillActivatesStripeProNormally() {
        UUID workspaceId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-13T00:00:00Z");
        Instant periodStart = Instant.parse("2026-08-12T00:00:00Z");
        Instant periodEnd = Instant.parse("2026-09-12T00:00:00Z");
        Subscription subscription = Subscription.free(workspaceId,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"));
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        when(subscriptions.findByWorkspaceId(workspaceId)).thenReturn(Optional.of(subscription));
        when(subscriptions.findLockedByWorkspaceId(workspaceId)).thenReturn(Optional.of(subscription));
        BillingService billing = new BillingService(subscriptions, mock(BillingUsageRepository.class),
                mock(BillingPaymentRepository.class), new BillingProperties(), new BillingPaymentProperties(),
                () -> now, mock(ObjectProvider.class), mock(ObjectProvider.class));

        billing.activatePro(workspaceId, PaymentProvider.STRIPE, "sub_fresh", periodStart, periodEnd);

        org.assertj.core.api.Assertions.assertThat(subscription.getPlan()).isEqualTo(Plan.PRO);
        org.assertj.core.api.Assertions.assertThat(subscription.getStatus())
                .isEqualTo(com.assetsphere.modules.billing.api.SubscriptionStatus.ACTIVE);
        org.assertj.core.api.Assertions.assertThat(subscription.getPaymentProvider())
                .isEqualTo(PaymentProvider.STRIPE.name());
        org.assertj.core.api.Assertions.assertThat(subscription.getExternalSubscriptionId()).isEqualTo("sub_fresh");
        org.assertj.core.api.Assertions.assertThat(subscription.getCurrentPeriodStart()).isEqualTo(periodStart);
        org.assertj.core.api.Assertions.assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(periodEnd);
    }

    @Test
    @SuppressWarnings("unchecked")
    void activeSubscriptionUsesAssociatedPaidPaymentInsteadOfLaterFailedAttempt() {
        UUID workspaceId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-12T10:15:30Z");
        Subscription subscription = Subscription.free(workspaceId,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"));
        subscription.activatePro(PaymentProvider.RAZORPAY_LOCAL.name(), "payment_paid", now,
                Instant.parse("2026-09-12T10:15:30Z"));
        BillingPayment paid = BillingPayment.create(workspaceId, UUID.randomUUID(), Plan.PRO,
                PaymentProvider.RAZORPAY_LOCAL, "paid", "receipt-paid", 99_900, "INR");
        paid.orderCreated("order_paid", null);
        paid.paid("payment_paid", now);
        BillingPayment failed = BillingPayment.create(workspaceId, UUID.randomUUID(), Plan.PRO,
                PaymentProvider.RAZORPAY_LOCAL, "failed", "receipt-failed", 99_900, "INR");
        failed.failed("PAYMENT_FAILED", now);
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        BillingPaymentRepository payments = mock(BillingPaymentRepository.class);
        BillingUsageRepository usage = mock(BillingUsageRepository.class);
        WorkspaceResourceUsageProvider resources = mock(WorkspaceResourceUsageProvider.class);
        ObjectProvider<WorkspaceResourceUsageProvider> resourceProviders = mock(ObjectProvider.class);
        when(subscriptions.findByWorkspaceId(workspaceId)).thenReturn(Optional.of(subscription));
        when(payments.findByProviderAndProviderPaymentId(PaymentProvider.RAZORPAY_LOCAL, "payment_paid"))
                .thenReturn(Optional.of(paid));
        when(payments.findFirstByWorkspaceIdOrderByCreatedAtDesc(workspaceId)).thenReturn(Optional.of(failed));
        when(usage.findUsage(org.mockito.ArgumentMatchers.eq(workspaceId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.<UsageMetric, Long>of());
        when(resources.usage(workspaceId)).thenReturn(new WorkspaceResourceUsageProvider.WorkspaceResourceUsage(0, 0));
        when(resourceProviders.orderedStream()).thenReturn(Stream.of(resources));
        BillingService billing = new BillingService(subscriptions, usage, payments, new BillingProperties(), new BillingPaymentProperties(),
                () -> now, resourceProviders, mock(ObjectProvider.class));

        org.assertj.core.api.Assertions.assertThat(billing.billing(workspaceId).latestPaymentStatus())
                .isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @SuppressWarnings("unchecked")
    void oldFailedAttemptDoesNotRemainCurrentForFreeWorkspace() {
        UUID workspaceId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-12T10:15:30Z");
        Subscription subscription = Subscription.free(workspaceId,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"));
        BillingPayment failed = BillingPayment.create(workspaceId, UUID.randomUUID(), Plan.PRO,
                PaymentProvider.RAZORPAY_LOCAL, "failed", "receipt-old", 99_900, "INR");
        failed.failed("PAYMENT_FAILED", now.minusSeconds(3600));
        org.springframework.test.util.ReflectionTestUtils.setField(failed, "createdAt", now.minusSeconds(3600));
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        BillingPaymentRepository payments = mock(BillingPaymentRepository.class);
        BillingUsageRepository usage = mock(BillingUsageRepository.class);
        WorkspaceResourceUsageProvider resources = mock(WorkspaceResourceUsageProvider.class);
        ObjectProvider<WorkspaceResourceUsageProvider> resourceProviders = mock(ObjectProvider.class);
        when(subscriptions.findByWorkspaceId(workspaceId)).thenReturn(Optional.of(subscription));
        when(payments.findFirstByWorkspaceIdOrderByCreatedAtDesc(workspaceId)).thenReturn(Optional.of(failed));
        when(usage.findUsage(org.mockito.ArgumentMatchers.eq(workspaceId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.<UsageMetric, Long>of());
        when(resources.usage(workspaceId)).thenReturn(new WorkspaceResourceUsageProvider.WorkspaceResourceUsage(0, 0));
        when(resourceProviders.orderedStream()).thenReturn(Stream.of(resources));
        BillingPaymentProperties paymentProperties = new BillingPaymentProperties();
        paymentProperties.setAttemptFeedbackWindow(java.time.Duration.ofMinutes(15));
        BillingService billing = new BillingService(subscriptions, usage, payments, new BillingProperties(),
                paymentProperties, () -> now, resourceProviders, mock(ObjectProvider.class));

        org.assertj.core.api.Assertions.assertThat(billing.billing(workspaceId).latestPaymentStatus()).isNull();
    }
}
