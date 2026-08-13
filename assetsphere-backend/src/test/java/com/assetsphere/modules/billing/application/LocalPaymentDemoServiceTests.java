package com.assetsphere.modules.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.assetsphere.modules.billing.api.LocalPaymentDemoGateway;
import com.assetsphere.modules.billing.api.LocalPaymentMethod;
import com.assetsphere.modules.billing.api.PaymentProvider;
import com.assetsphere.modules.billing.api.Plan;
import com.assetsphere.modules.billing.api.dto.request.LocalPaymentRequest;
import com.assetsphere.modules.billing.domain.BillingPayment;
import com.assetsphere.modules.billing.persistence.BillingPaymentRepository;
import com.assetsphere.modules.common.exception.AuthorizationDeniedException;
import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class LocalPaymentDemoServiceTests {
    @Test
    void mapsSupportedMethodsAndNormalizesProviderResponse() {
        int expiryYear = java.time.Year.now().getValue() + 1;
        for (var testCase : java.util.List.of(
                new Case(LocalPaymentMethod.UPI, new LocalPaymentRequest("order_1", LocalPaymentMethod.UPI,
                        "demo@bank", null, null, null, null, null, null, null), Map.of("VPA", "demo@bank")),
                new Case(LocalPaymentMethod.NETBANKING, new LocalPaymentRequest("order_1", LocalPaymentMethod.NETBANKING,
                        null, "DEMO_BANK", null, null, null, null, null, null), Map.of("BANK", "DEMO_BANK")),
                new Case(LocalPaymentMethod.WALLET, new LocalPaymentRequest("order_1", LocalPaymentMethod.WALLET,
                        null, null, "DEMO_WALLET", null, null, null, null, null), Map.of("WALLET_CODE", "DEMO_WALLET")),
                new Case(LocalPaymentMethod.CARD, new LocalPaymentRequest("order_1", LocalPaymentMethod.CARD,
                        null, null, null, "4111111111111111", "123", 12, expiryYear, "Demo User"),
                        Map.of("PAN", "4111111111111111", "CVV", "123", "EXPIRY_MONTH", "12",
                                "EXPIRY_YEAR", String.valueOf(expiryYear), "CARD_HOLDER_NAME", "Demo User")))) {
            Fixture fixture = fixture(PaymentProvider.RAZORPAY_LOCAL, UUID.randomUUID(), "order_1", true);
            when(fixture.gateway.cardEnabled()).thenReturn(testCase.method == LocalPaymentMethod.CARD);
            when(fixture.gateway.create(anyString(), org.mockito.ArgumentMatchers.eq(testCase.method),
                    org.mockito.ArgumentMatchers.eq(testCase.details), anyString()))
                    .thenReturn(new LocalPaymentDemoGateway.LocalPaymentResult("payment_1", "order_1", "AUTHORIZING",
                            testCase.method, 99_900, "INR", Instant.parse("2026-08-11T00:00:00Z")));
            when(fixture.paymentTransaction.initiateLocalPayment(org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.eq(fixture.gateway), org.mockito.ArgumentMatchers.eq(testCase.method),
                    org.mockito.ArgumentMatchers.eq(testCase.details), anyString()))
                    .thenAnswer(invocation -> new BillingPaymentTransaction.LocalPaymentInitiation("payment_1",
                            fixture.gateway.create("order_1", testCase.method, testCase.details,
                                    (String) invocation.getArgument(4)), true));

            var response = fixture.service.create(fixture.workspaceId, testCase.request);

            assertThat(response.paymentId()).isEqualTo("payment_1");
            assertThat(response.providerPaymentStatus()).isEqualTo("AUTHORIZING");
            assertThat(response.method()).isEqualTo(testCase.method);
        }
    }

    @Test
    void rejectsWrongWorkspaceAndNonPendingOrders() {
        Fixture wrongWorkspace = fixture(PaymentProvider.RAZORPAY_LOCAL, UUID.randomUUID(), "order_1", true);
        assertThatThrownBy(() -> wrongWorkspace.service.create(UUID.randomUUID(),
                request(LocalPaymentMethod.UPI)))
                .isInstanceOf(AuthorizationDeniedException.class);

        Fixture notPending = fixture(PaymentProvider.RAZORPAY_LOCAL, UUID.randomUUID(), "order_1", false);
        assertThatThrownBy(() -> notPending.service.create(notPending.workspaceId,
                request(LocalPaymentMethod.UPI)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void rejectsNonLocalProvider() {
        Fixture fixture = fixture(PaymentProvider.STRIPE, UUID.randomUUID(), "order_1", true);
        assertThatThrownBy(() -> fixture.service.create(fixture.workspaceId,
                request(LocalPaymentMethod.UPI)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void capturedActivatesOnlyWhenTrustedPollingIsEnabledAndFailureNeverActivates() {
        Fixture enabled = fixture(PaymentProvider.RAZORPAY_LOCAL, UUID.randomUUID(), "order_1", true);
        when(enabled.gateway.get("order_1", "payment_1")).thenReturn(new LocalPaymentDemoGateway.LocalPaymentResult(
                "payment_1", "order_1", "CAPTURED", LocalPaymentMethod.UPI, 99_900, "INR", Instant.now()));
        when(enabled.gateway.pollConfirmationEnabled()).thenReturn(true);
        enabled.service.get(enabled.workspaceId, "order_1", "payment_1");
        org.mockito.Mockito.verify(enabled.confirmations).succeeded(PaymentProvider.RAZORPAY_LOCAL,
                "order_1", "payment_1", 99_900, "INR");

        Fixture disabled = fixture(PaymentProvider.RAZORPAY_LOCAL, UUID.randomUUID(), "order_1", true);
        when(disabled.gateway.get("order_1", "payment_1")).thenReturn(new LocalPaymentDemoGateway.LocalPaymentResult(
                "payment_1", "order_1", "CAPTURED", LocalPaymentMethod.UPI, 99_900, "INR", Instant.now()));
        disabled.service.get(disabled.workspaceId, "order_1", "payment_1");
        org.mockito.Mockito.verify(disabled.confirmations, org.mockito.Mockito.never()).succeeded(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());

        Fixture failed = fixture(PaymentProvider.RAZORPAY_LOCAL, UUID.randomUUID(), "order_1", true);
        when(failed.gateway.get("order_1", "payment_1")).thenReturn(new LocalPaymentDemoGateway.LocalPaymentResult(
                "payment_1", "order_1", "FAILED", LocalPaymentMethod.UPI, 99_900, "INR", Instant.now()));
        failed.service.get(failed.workspaceId, "order_1", "payment_1");
        org.mockito.Mockito.verify(failed.confirmations).failed(PaymentProvider.RAZORPAY_LOCAL,
                "order_1", "payment_1", 99_900, "INR", "FAILED");
    }

    @SuppressWarnings("unchecked")
    private Fixture fixture(PaymentProvider provider, UUID workspaceId, String orderId, boolean pending) {
        BillingPayment payment = BillingPayment.create(workspaceId, UUID.randomUUID(), Plan.PRO, provider,
                "key", "receipt", 99_900, "INR");
        if (pending) payment.orderCreated(orderId, null);
        BillingPaymentRepository payments = mock(BillingPaymentRepository.class);
        when(payments.findByProviderAndProviderOrderId(PaymentProvider.RAZORPAY_LOCAL, orderId))
                .thenReturn(Optional.of(payment));
        LocalPaymentDemoGateway gateway = mock(LocalPaymentDemoGateway.class);
        ObjectProvider<LocalPaymentDemoGateway> gateways = mock(ObjectProvider.class);
        when(gateways.orderedStream()).thenReturn(Stream.of(gateway));
        BillingPaymentProperties properties = new BillingPaymentProperties();
        properties.setProPriceMinor(99_900);
        properties.setCurrency("INR");
        BillingPaymentTransaction paymentTransaction = mock(BillingPaymentTransaction.class);
        ProviderPaymentConfirmationService confirmations = mock(ProviderPaymentConfirmationService.class);
        return new Fixture(new LocalPaymentDemoService(payments, properties, gateways, paymentTransaction, confirmations),
                gateway, paymentTransaction, confirmations, workspaceId);
    }

    private LocalPaymentRequest request(LocalPaymentMethod method) {
        return new LocalPaymentRequest("order_1", method, "demo@bank", null, null,
                null, null, null, null, null);
    }

    private record Fixture(LocalPaymentDemoService service, LocalPaymentDemoGateway gateway,
                           BillingPaymentTransaction paymentTransaction,
                           ProviderPaymentConfirmationService confirmations, UUID workspaceId) { }
    private record Case(LocalPaymentMethod method, LocalPaymentRequest request, Map<String, String> details) { }
}
