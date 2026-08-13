package com.assetsphere.modules.billing.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetsphere.modules.billing.api.PaymentProvider;
import com.assetsphere.modules.billing.api.Plan;
import com.assetsphere.modules.billing.domain.BillingPayment;
import com.assetsphere.modules.billing.persistence.BillingPaymentRepository;
import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.common.exception.ConflictException;
import com.assetsphere.modules.common.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProviderPaymentConfirmationServiceTests {
    private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");
    private static final Instant PERIOD_START = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant PERIOD_END = Instant.parse("2026-09-01T00:00:00Z");
    @Test
    void matchingConfirmationActivatesOnceAndIsIdempotent() {
        BillingPayment payment = payment();
        BillingPaymentRepository payments = mock(BillingPaymentRepository.class);
        BillingService billing = mock(BillingService.class);
        when(payments.findLockedByProviderAndProviderOrderId(PaymentProvider.RAZORPAY_LOCAL, "order_1"))
                .thenReturn(Optional.of(payment));
        when(billing.currentPlan(payment.getWorkspaceId())).thenReturn(Plan.FREE);
        var service = new ProviderPaymentConfirmationService(payments, billing,
                () -> Instant.parse("2026-08-11T00:00:00Z"));

        service.succeeded(PaymentProvider.RAZORPAY_LOCAL, "order_1", "payment_1", 99_900, "INR");
        service.succeeded(PaymentProvider.RAZORPAY_LOCAL, "order_1", "payment_1", 99_900, "INR");

        verify(billing, times(1)).activatePro(payment.getWorkspaceId(), PaymentProvider.RAZORPAY_LOCAL, "payment_1",
                null, null);
    }

    @Test
    void mismatchedAmountOrCurrencyNeverActivates() {
        BillingPayment payment = payment();
        BillingPaymentRepository payments = mock(BillingPaymentRepository.class);
        BillingService billing = mock(BillingService.class);
        when(payments.findLockedByProviderAndProviderOrderId(PaymentProvider.RAZORPAY_LOCAL, "order_1"))
                .thenReturn(Optional.of(payment));
        var service = new ProviderPaymentConfirmationService(payments, billing, Instant::now);

        assertThatThrownBy(() -> service.succeeded(PaymentProvider.RAZORPAY_LOCAL,
                "order_1", "payment_1", 1, "INR")).isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> service.succeeded(PaymentProvider.RAZORPAY_LOCAL,
                "order_1", "payment_1", 99_900, "USD")).isInstanceOf(BusinessRuleViolationException.class);
        verify(billing, org.mockito.Mockito.never()).activatePro(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void stripeSuccessRequiresSubscriptionIdentityBeforeActivation() {
        BillingPaymentRepository payments = mock(BillingPaymentRepository.class);
        BillingService billing = mock(BillingService.class);
        var service = new ProviderPaymentConfirmationService(payments, billing, Instant::now);

        assertThatThrownBy(() -> service.succeeded(PaymentProvider.STRIPE,
                "cs_1", null, 99_900, "INR"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("subscription identity");
        assertThatThrownBy(() -> service.succeeded(PaymentProvider.STRIPE,
                "cs_1", "pi_123", 99_900, "INR"))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(billing, org.mockito.Mockito.never()).activatePro(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void repairsLegacyPaidStripeIdentityOnceAndRejectsReplacement() {
        BillingPayment payment = legacyPaidStripePayment();
        BillingPaymentRepository payments = mock(BillingPaymentRepository.class);
        BillingService billing = mock(BillingService.class);
        when(payments.findLockedByProviderAndProviderOrderId(PaymentProvider.STRIPE, "cs_123"))
                .thenReturn(Optional.of(payment));
        var service = new ProviderPaymentConfirmationService(payments, billing, () -> NOW);

        service.succeeded(PaymentProvider.STRIPE, "cs_123", "sub_123", 99_900, "INR",
                PERIOD_START, PERIOD_END);

        assertThat(payment.getProviderPaymentId()).isEqualTo("sub_123");
        verify(billing).recoverStripeSubscriptionIdentity(payment.getWorkspaceId(), "sub_123",
                PERIOD_START, PERIOD_END);

        service.succeeded(PaymentProvider.STRIPE, "cs_123", "sub_123", 99_900, "INR",
                PERIOD_START, PERIOD_END);
        verify(billing, times(1)).recoverStripeSubscriptionIdentity(payment.getWorkspaceId(), "sub_123",
                PERIOD_START, PERIOD_END);

        assertThatThrownBy(() -> service.succeeded(PaymentProvider.STRIPE,
                "cs_123", "sub_456", 99_900, "INR", PERIOD_START, PERIOD_END))
                .isInstanceOf(ConflictException.class);
        assertThat(payment.getProviderPaymentId()).isEqualTo("sub_123");
    }

    @Test
    void unrelatedCheckoutCannotClaimLegacySubscriptionIdentity() {
        BillingPaymentRepository payments = mock(BillingPaymentRepository.class);
        BillingService billing = mock(BillingService.class);
        when(payments.findLockedByProviderAndProviderOrderId(PaymentProvider.STRIPE, "cs_unrelated"))
                .thenReturn(Optional.empty());
        var service = new ProviderPaymentConfirmationService(payments, billing, () -> NOW);

        assertThatThrownBy(() -> service.succeeded(PaymentProvider.STRIPE,
                "cs_unrelated", "sub_123", 99_900, "INR", PERIOD_START, PERIOD_END))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(billing, org.mockito.Mockito.never()).recoverStripeSubscriptionIdentity(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void freshStripePaymentStillActivatesNormally() {
        BillingPayment payment = BillingPayment.create(UUID.randomUUID(), UUID.randomUUID(), Plan.PRO,
                PaymentProvider.STRIPE, "key", "receipt", 99_900, "INR");
        payment.orderCreated("cs_fresh", null);
        BillingPaymentRepository payments = mock(BillingPaymentRepository.class);
        BillingService billing = mock(BillingService.class);
        when(payments.findLockedByProviderAndProviderOrderId(PaymentProvider.STRIPE, "cs_fresh"))
                .thenReturn(Optional.of(payment));
        var service = new ProviderPaymentConfirmationService(payments, billing, () -> NOW);

        service.succeeded(PaymentProvider.STRIPE, "cs_fresh", "sub_fresh", 99_900, "INR",
                PERIOD_START, PERIOD_END);

        verify(billing).activatePro(payment.getWorkspaceId(), PaymentProvider.STRIPE, "sub_fresh",
                PERIOD_START, PERIOD_END);
        assertThat(payment.getStatus()).isEqualTo(com.assetsphere.modules.billing.api.PaymentStatus.PAID);
        assertThat(payment.getProviderPaymentId()).isEqualTo("sub_fresh");
    }

    private BillingPayment payment() {
        BillingPayment payment = BillingPayment.create(UUID.randomUUID(), UUID.randomUUID(), Plan.PRO,
                PaymentProvider.RAZORPAY_LOCAL, "key", "receipt", 99_900, "INR");
        payment.orderCreated("order_1", null);
        payment.providerPaymentCreated("payment_1");
        return payment;
    }

    private BillingPayment legacyPaidStripePayment() {
        BillingPayment payment = BillingPayment.create(UUID.randomUUID(), UUID.randomUUID(), Plan.PRO,
                PaymentProvider.STRIPE, "key", "receipt", 99_900, "INR");
        payment.orderCreated("cs_123", null);
        payment.paid("sub_original", NOW);
        org.springframework.test.util.ReflectionTestUtils.setField(payment, "providerPaymentId", "NULL");
        return payment;
    }
}
