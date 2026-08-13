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
