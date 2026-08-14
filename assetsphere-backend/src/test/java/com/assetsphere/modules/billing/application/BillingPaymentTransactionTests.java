package com.assetsphere.modules.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetsphere.modules.billing.api.LocalPaymentDemoGateway;
import com.assetsphere.modules.billing.api.LocalPaymentMethod;
import com.assetsphere.modules.billing.api.PaymentProvider;
import com.assetsphere.modules.billing.api.Plan;
import com.assetsphere.modules.billing.domain.BillingPayment;
import com.assetsphere.modules.billing.domain.Subscription;
import com.assetsphere.modules.billing.persistence.BillingPaymentRepository;
import com.assetsphere.modules.billing.persistence.SubscriptionRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BillingPaymentTransactionTests {
    @Test
    void lockedPaidSubscriptionRejectsCheckoutAfterEarlierFreeRead() {
        UUID workspaceId = UUID.randomUUID();
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        BillingPaymentRepository payments = mock(BillingPaymentRepository.class);
        Subscription subscription = Subscription.free(workspaceId, Instant.EPOCH,
                Instant.parse("2026-09-01T00:00:00Z"));
        subscription.activatePro(PaymentProvider.STRIPE.name(), "sub_123", Instant.EPOCH,
                Instant.parse("2026-09-01T00:00:00Z"));
        when(subscriptions.findLockedByWorkspaceId(workspaceId)).thenReturn(Optional.of(subscription));
        var transaction = new BillingPaymentTransaction(payments, subscriptions,
                new BillingPaymentProperties(), () -> Instant.EPOCH);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transaction.reserve(
                        workspaceId, UUID.randomUUID(), "checkout-key", mock(com.assetsphere.modules.billing.api.PaymentGateway.class),
                        99_900, "INR"))
                .isInstanceOf(com.assetsphere.modules.common.exception.BusinessRuleViolationException.class)
                .hasMessage("This workspace already has a paid plan");
        verify(payments, never()).createIfAbsent(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void lockedWorkspaceReusesSinglePendingCheckoutReservation() {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        BillingPaymentRepository payments = mock(BillingPaymentRepository.class);
        Subscription subscription = Subscription.free(workspaceId, Instant.EPOCH,
                Instant.parse("2026-09-01T00:00:00Z"));
        BillingPayment pending = BillingPayment.create(workspaceId, userId, Plan.PRO,
                PaymentProvider.STRIPE, "first-key", "receipt", 99_900, "INR");
        pending.orderCreated("checkout_123", "https://checkout.stripe.com/c/pay/checkout_123");
        var gateway = mock(com.assetsphere.modules.billing.api.PaymentGateway.class);
        when(gateway.provider()).thenReturn(PaymentProvider.STRIPE);
        when(subscriptions.findLockedByWorkspaceId(workspaceId)).thenReturn(Optional.of(subscription));
        when(payments.findByWorkspaceIdAndIdempotencyKey(workspaceId, "second-key"))
                .thenReturn(Optional.empty());
        when(payments.findFirstByWorkspaceIdAndProviderAndStatusInAndCreatedAtAfterOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(workspaceId), org.mockito.ArgumentMatchers.eq(PaymentProvider.STRIPE),
                org.mockito.ArgumentMatchers.anyCollection(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.of(pending));
        var transaction = new BillingPaymentTransaction(payments, subscriptions,
                new BillingPaymentProperties(), () -> Instant.EPOCH);

        var reservation = transaction.reserve(workspaceId, userId, "second-key", gateway, 99_900, "INR");

        assertThat(reservation.created()).isFalse();
        assertThat(reservation.payment()).isSameAs(pending);
        verify(payments, never()).createIfAbsent(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void orderWithoutProviderPaymentCreatesExactlyOneProviderPayment() {
        BillingPaymentRepository payments = mock(BillingPaymentRepository.class);
        LocalPaymentDemoGateway gateway = mock(LocalPaymentDemoGateway.class);
        BillingPayment payment = pendingPayment();
        when(payments.findLockedById(payment.getId())).thenReturn(Optional.of(payment));
        var providerResult = result("payment-1");
        when(gateway.create(payment.getProviderOrderId(), LocalPaymentMethod.UPI,
                Map.of("VPA", "demo@bank"), "operation-1")).thenReturn(providerResult);

        var initiation = transaction(payments).initiateLocalPayment(payment.getId(), gateway,
                LocalPaymentMethod.UPI, Map.of("VPA", "demo@bank"), "operation-1");

        assertThat(initiation.created()).isTrue();
        assertThat(payment.getProviderPaymentId()).isEqualTo("payment-1");
        verify(gateway).create(payment.getProviderOrderId(), LocalPaymentMethod.UPI,
                Map.of("VPA", "demo@bank"), "operation-1");
    }

    @Test
    void attachedProviderPaymentIsResumedWithoutCreatingAnother() {
        BillingPaymentRepository payments = mock(BillingPaymentRepository.class);
        LocalPaymentDemoGateway gateway = mock(LocalPaymentDemoGateway.class);
        BillingPayment payment = pendingPayment();
        payment.providerPaymentCreated("payment-1");
        when(payments.findLockedById(payment.getId())).thenReturn(Optional.of(payment));

        var initiation = transaction(payments).initiateLocalPayment(payment.getId(), gateway,
                LocalPaymentMethod.WALLET, Map.of("WALLET_CODE", "DEMO"), "operation-2");

        assertThat(initiation.created()).isFalse();
        assertThat(initiation.paymentId()).isEqualTo("payment-1");
        verify(gateway, never()).create(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anyString());
    }

    private BillingPaymentTransaction transaction(BillingPaymentRepository payments) {
        return new BillingPaymentTransaction(payments, mock(SubscriptionRepository.class),
                new BillingPaymentProperties(), () -> Instant.EPOCH);
    }

    private BillingPayment pendingPayment() {
        BillingPayment payment = BillingPayment.create(UUID.randomUUID(), UUID.randomUUID(), Plan.PRO,
                PaymentProvider.RAZORPAY_LOCAL, "key", "receipt", 99_900, "INR");
        ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
        payment.orderCreated("order-1", null);
        return payment;
    }

    private LocalPaymentDemoGateway.LocalPaymentResult result(String paymentId) {
        return new LocalPaymentDemoGateway.LocalPaymentResult(paymentId, "order-1", "AUTHORIZING",
                LocalPaymentMethod.UPI, 99_900, "INR", Instant.EPOCH);
    }
}
