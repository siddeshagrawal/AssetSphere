package com.assetsphere.modules.billing.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetsphere.modules.billing.api.PaymentGateway;
import com.assetsphere.modules.billing.api.PaymentProvider;
import com.assetsphere.modules.billing.api.PaymentWebhookEvent;
import com.assetsphere.modules.billing.api.PaymentWebhookStatus;
import com.assetsphere.modules.billing.api.Plan;
import com.assetsphere.modules.billing.api.ProviderSubscriptionStatus;
import com.assetsphere.modules.billing.domain.BillingPayment;
import com.assetsphere.modules.billing.persistence.BillingPaymentRepository;
import com.assetsphere.modules.billing.persistence.BillingProviderEventRepository;
import com.assetsphere.modules.billing.persistence.BillingWebhookRepository;
import com.assetsphere.modules.common.time.ClockProvider;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class BillingWebhookServiceTests {
    private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");
    private static final Instant PERIOD_START = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant PERIOD_END = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    @SuppressWarnings("unchecked")
    void activatesProOnlyFromClaimedVerifiedMatchingPayment() {
        UUID workspaceId = UUID.randomUUID();
        BillingPayment payment = BillingPayment.create(workspaceId, UUID.randomUUID(), Plan.PRO,
                PaymentProvider.RAZORPAY_LOCAL, "key", "receipt", 99_900, "INR");
        payment.orderCreated("order_123", null);
        PaymentWebhookEvent event = new PaymentWebhookEvent(PaymentProvider.RAZORPAY_LOCAL, "event_123",
                "PAYMENT_STATUS_CHANGED", "order_123", "pay_123", 99_900, "INR",
                PaymentWebhookStatus.SUCCEEDED, NOW, true);
        Fixture fixture = fixture(event);
        when(fixture.webhookEvents.claim(PaymentProvider.RAZORPAY_LOCAL, "event_123", "PAYMENT_STATUS_CHANGED",
                fixture.payloadHash, NOW)).thenReturn(true);
        when(fixture.payments.findLockedByProviderAndProviderOrderId(PaymentProvider.RAZORPAY_LOCAL, "order_123"))
                .thenReturn(Optional.of(payment));

        fixture.service.handle(PaymentProvider.RAZORPAY_LOCAL, "event_123", "{}", "signature");

        verify(fixture.confirmations).succeeded(PaymentProvider.RAZORPAY_LOCAL, "order_123", "pay_123", 99_900,
                "INR", null, null);
        verify(fixture.webhookEvents).complete(PaymentProvider.RAZORPAY_LOCAL, "event_123", true, NOW);
    }

    @Test
    void checkoutConfirmationDoesNotAssumeSubscriptionPeriodFields() {
        BillingPayment payment = BillingPayment.create(UUID.randomUUID(), UUID.randomUUID(), Plan.PRO,
                PaymentProvider.STRIPE, "key", "receipt", 99_900, "INR");
        payment.orderCreated("checkout_123", null);
        PaymentWebhookEvent event = new PaymentWebhookEvent(PaymentProvider.STRIPE, "event_123",
                "checkout.session.completed", "checkout_123", "sub_123", 99_900, "INR",
                PaymentWebhookStatus.SUCCEEDED, NOW, true);
        Fixture fixture = fixture(event);
        when(fixture.webhookEvents.claim(PaymentProvider.STRIPE, "event_123", event.eventType(),
                fixture.payloadHash, NOW)).thenReturn(true);
        when(fixture.payments.findLockedByProviderAndProviderOrderId(PaymentProvider.STRIPE, "checkout_123"))
                .thenReturn(Optional.of(payment));

        fixture.service.handle(PaymentProvider.STRIPE, "event_123", "{}", "signature");

        verify(fixture.confirmations).succeeded(PaymentProvider.STRIPE, "checkout_123", "sub_123",
                99_900, "INR", null, null);
    }

    @Test
    void invoicePaidNeverPassesInvoiceAggregationPeriodToSubscriptionRenewal() {
        BillingPayment payment = stripePaidPayment();
        PaymentWebhookEvent event = new PaymentWebhookEvent(PaymentProvider.STRIPE, "event_invoice",
                "invoice.paid", null, "sub_123", 99_900, "INR",
                PaymentWebhookStatus.SUCCEEDED, NOW, true, NOW, NOW, null);
        Fixture fixture = fixture(event);
        when(fixture.webhookEvents.claim(PaymentProvider.STRIPE, event.eventId(), event.eventType(),
                fixture.payloadHash, NOW)).thenReturn(true);
        when(fixture.payments.findLockedByProviderAndProviderPaymentId(PaymentProvider.STRIPE, "sub_123"))
                .thenReturn(Optional.of(payment));

        fixture.service.handle(PaymentProvider.STRIPE, event.eventId(), "{}", "signature");

        verify(fixture.billing).renewPaidPlan(payment.getWorkspaceId(), PaymentProvider.STRIPE, "sub_123");
        verify(fixture.billing, never()).renewPaidPlan(payment.getWorkspaceId(), PaymentProvider.STRIPE,
                "sub_123", NOW, NOW);
    }

    @Test
    void duplicateAndStaleInvoicePaidEventsDoNotRenewAgain() {
        PaymentWebhookEvent duplicate = new PaymentWebhookEvent(PaymentProvider.STRIPE, "event_duplicate_invoice",
                "invoice.paid", null, "sub_123", 99_900, "INR",
                PaymentWebhookStatus.SUCCEEDED, NOW, true);
        Fixture duplicateFixture = fixture(duplicate);
        when(duplicateFixture.webhookEvents.claim(PaymentProvider.STRIPE, duplicate.eventId(), duplicate.eventType(),
                duplicateFixture.payloadHash, NOW)).thenReturn(false);

        duplicateFixture.service.handle(PaymentProvider.STRIPE, duplicate.eventId(), "{}", "signature");

        verify(duplicateFixture.billing, never()).renewPaidPlan(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());

        PaymentWebhookEvent stale = new PaymentWebhookEvent(PaymentProvider.STRIPE, "event_stale_invoice",
                "invoice.paid", null, "sub_123", 99_900, "INR",
                PaymentWebhookStatus.SUCCEEDED, NOW.minusSeconds(60), true);
        Fixture staleFixture = fixture(stale);
        when(staleFixture.webhookEvents.claim(PaymentProvider.STRIPE, stale.eventId(), stale.eventType(),
                staleFixture.payloadHash, NOW)).thenReturn(true);
        when(staleFixture.providerEvents.accept(PaymentProvider.STRIPE, "SUBSCRIPTION:sub_123",
                stale.occurredAt(), 300, stale.eventId())).thenReturn(false);

        staleFixture.service.handle(PaymentProvider.STRIPE, stale.eventId(), "{}", "signature");

        verify(staleFixture.billing, never()).renewPaidPlan(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void duplicateWebhookIsHarmless() {
        PaymentWebhookEvent event = new PaymentWebhookEvent(PaymentProvider.RAZORPAY_LOCAL, "event_123",
                "PAYMENT_STATUS_CHANGED", "order_123", "pay_123", 99_900, "INR",
                PaymentWebhookStatus.SUCCEEDED, NOW, true);
        Fixture fixture = fixture(event);
        when(fixture.webhookEvents.claim(PaymentProvider.RAZORPAY_LOCAL, "event_123", "PAYMENT_STATUS_CHANGED",
                fixture.payloadHash, NOW)).thenReturn(false);

        fixture.service.handle(PaymentProvider.RAZORPAY_LOCAL, "event_123", "{}", "signature");

        verify(fixture.confirmations, never()).succeeded(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void stripeSuccessWithoutSubscriptionIdentityFailsForProviderRetry() {
        PaymentWebhookEvent event = new PaymentWebhookEvent(PaymentProvider.STRIPE, "event_123",
                "checkout.session.completed", "checkout_123", null, 99_900, "INR",
                PaymentWebhookStatus.SUCCEEDED, NOW, true);
        Fixture fixture = fixture(event);
        when(fixture.webhookEvents.claim(PaymentProvider.STRIPE, "event_123", event.eventType(),
                fixture.payloadHash, NOW)).thenReturn(true);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                fixture.service.handle(PaymentProvider.STRIPE, "event_123", "{}", "signature"))
                .isInstanceOf(com.assetsphere.modules.common.exception.ServiceUnavailableException.class);
        verify(fixture.confirmations, never()).succeeded(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void subscriptionUpdatedSynchronizesCancellationAndDeletedCancelsTerminally() {
        BillingPayment payment = stripePaidPayment();
        PaymentWebhookEvent updated = new PaymentWebhookEvent(PaymentProvider.STRIPE, "event_123",
                "customer.subscription.updated", null, "sub_123", 0, "INR",
                PaymentWebhookStatus.IGNORED, NOW, true, PERIOD_START, PERIOD_END, true,
                ProviderSubscriptionStatus.ACTIVE);
        Fixture updateFixture = fixture(updated);
        when(updateFixture.webhookEvents.claim(PaymentProvider.STRIPE, "event_123", updated.eventType(),
                updateFixture.payloadHash, NOW)).thenReturn(true);
        when(updateFixture.payments.findLockedByProviderAndProviderPaymentId(PaymentProvider.STRIPE, "sub_123"))
                .thenReturn(Optional.of(payment));

        updateFixture.service.handle(PaymentProvider.STRIPE, "event_123", "{}", "signature");

        verify(updateFixture.billing).synchronizeStripeSubscription(payment.getWorkspaceId(), "sub_123",
                PERIOD_START, PERIOD_END, true, ProviderSubscriptionStatus.ACTIVE);

        PaymentWebhookEvent deleted = new PaymentWebhookEvent(PaymentProvider.STRIPE, "event_124",
                "customer.subscription.deleted", null, "sub_123", 0, "INR",
                PaymentWebhookStatus.CANCELED, NOW, true);
        Fixture deleteFixture = fixture(deleted);
        when(deleteFixture.webhookEvents.claim(PaymentProvider.STRIPE, "event_124", deleted.eventType(),
                deleteFixture.payloadHash, NOW)).thenReturn(true);
        when(deleteFixture.payments.findLockedByProviderAndProviderPaymentId(PaymentProvider.STRIPE, "sub_123"))
                .thenReturn(Optional.of(payment));

        deleteFixture.service.handle(PaymentProvider.STRIPE, "event_124", "{}", "signature");

        verify(deleteFixture.billing).cancelPaidPlan(payment.getWorkspaceId(), PaymentProvider.STRIPE, "sub_123");
    }

    @Test
    void pastDueSubscriptionUpdateCannotReactivatePaidEntitlement() {
        BillingPayment payment = stripePaidPayment();
        PaymentWebhookEvent event = new PaymentWebhookEvent(PaymentProvider.STRIPE, "event_past_due",
                "customer.subscription.updated", null, "sub_123", 0, "INR",
                PaymentWebhookStatus.FAILED, NOW, true, PERIOD_START, PERIOD_END, false,
                ProviderSubscriptionStatus.PAST_DUE);
        Fixture fixture = fixture(event);
        when(fixture.webhookEvents.claim(PaymentProvider.STRIPE, event.eventId(), event.eventType(),
                fixture.payloadHash, NOW)).thenReturn(true);
        when(fixture.payments.findLockedByProviderAndProviderPaymentId(PaymentProvider.STRIPE, "sub_123"))
                .thenReturn(Optional.of(payment));

        fixture.service.handle(PaymentProvider.STRIPE, event.eventId(), "{}", "signature");

        verify(fixture.billing).synchronizeStripeSubscription(payment.getWorkspaceId(), "sub_123",
                PERIOD_START, PERIOD_END, false, ProviderSubscriptionStatus.PAST_DUE);
        verify(fixture.billing, never()).renewPaidPlan(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void staleStripeEventIsCompletedWithoutChangingSubscriptionState() {
        PaymentWebhookEvent event = new PaymentWebhookEvent(PaymentProvider.STRIPE, "event_stale",
                "customer.subscription.updated", null, "sub_123", 0, "INR",
                PaymentWebhookStatus.IGNORED, NOW.minusSeconds(60), true, PERIOD_START, PERIOD_END, false,
                ProviderSubscriptionStatus.ACTIVE);
        Fixture fixture = fixture(event);
        when(fixture.webhookEvents.claim(PaymentProvider.STRIPE, event.eventId(), event.eventType(),
                fixture.payloadHash, NOW)).thenReturn(true);
        when(fixture.providerEvents.accept(PaymentProvider.STRIPE, "SUBSCRIPTION:sub_123",
                event.occurredAt(), 350, event.eventId())).thenReturn(false);

        fixture.service.handle(PaymentProvider.STRIPE, event.eventId(), "{}", "signature");

        verify(fixture.billing, never()).synchronizeStripeSubscription(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.any());
        verify(fixture.webhookEvents).complete(PaymentProvider.STRIPE, event.eventId(), false, NOW);
    }

    @Test
    void nonSuccessfulLocalEventsNeverActivatePro() {
        for (PaymentWebhookEvent event : java.util.List.of(
                new PaymentWebhookEvent(PaymentProvider.RAZORPAY_LOCAL, "event_123", "ORDER_CREATED",
                        "order_123", null, 99_900, "INR", PaymentWebhookStatus.IGNORED, NOW, true),
                new PaymentWebhookEvent(PaymentProvider.RAZORPAY_LOCAL, "event_123", "PAYMENT_CREATED",
                        "order_123", "pay_123", 99_900, "INR", PaymentWebhookStatus.IGNORED, NOW, true),
                new PaymentWebhookEvent(PaymentProvider.RAZORPAY_LOCAL, "event_123", "PAYMENT_STATUS_CHANGED",
                        "order_123", "pay_123", 99_900, "INR", PaymentWebhookStatus.FAILED, NOW, true),
                new PaymentWebhookEvent(PaymentProvider.RAZORPAY_LOCAL, "event_123", "PAYMENT_STATUS_CHANGED",
                        "order_123", "pay_123", 99_900, "INR", PaymentWebhookStatus.CANCELED, NOW, true))) {
            Fixture fixture = fixture(event);
            when(fixture.webhookEvents.claim(PaymentProvider.RAZORPAY_LOCAL, "event_123", event.eventType(),
                    fixture.payloadHash, NOW)).thenReturn(true);
            BillingPayment payment = BillingPayment.create(UUID.randomUUID(), UUID.randomUUID(), Plan.PRO,
                    PaymentProvider.RAZORPAY_LOCAL, "key", "receipt", 99_900, "INR");
            payment.orderCreated("order_123", null);
            when(fixture.payments.findLockedByProviderAndProviderOrderId(PaymentProvider.RAZORPAY_LOCAL, "order_123"))
                    .thenReturn(Optional.of(payment));

            fixture.service.handle(PaymentProvider.RAZORPAY_LOCAL, "event_123", "{}", "signature");

            verify(fixture.confirmations, never()).succeeded(org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        }
    }

    @SuppressWarnings("unchecked")
    private Fixture fixture(PaymentWebhookEvent event) {
        ObjectProvider<PaymentGateway> gateways = mock(ObjectProvider.class);
        PaymentGateway gateway = mock(PaymentGateway.class);
        BillingPaymentRepository payments = mock(BillingPaymentRepository.class);
        BillingWebhookRepository webhookEvents = mock(BillingWebhookRepository.class);
        BillingProviderEventRepository providerEvents = mock(BillingProviderEventRepository.class);
        BillingService billing = mock(BillingService.class);
        ProviderPaymentConfirmationService confirmations = mock(ProviderPaymentConfirmationService.class);
        when(gateways.orderedStream()).thenReturn(Stream.of(gateway));
        when(gateway.provider()).thenReturn(event.provider());
        when(gateway.verifyWebhook(event.eventId(), "{}", "signature")).thenReturn(event);
        when(providerEvents.accept(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        BillingWebhookService service = new BillingWebhookService(gateways, payments, webhookEvents, providerEvents,
                billing, confirmations, (ClockProvider) () -> NOW);
        return new Fixture(service, payments, webhookEvents, providerEvents, billing, confirmations,
                "44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a");
    }

    private BillingPayment stripePaidPayment() {
        BillingPayment payment = BillingPayment.create(UUID.randomUUID(), UUID.randomUUID(), Plan.PRO,
                PaymentProvider.STRIPE, "key", "receipt", 99_900, "INR");
        payment.orderCreated("checkout_123", null);
        payment.paid("sub_123", NOW);
        return payment;
    }

    private record Fixture(BillingWebhookService service, BillingPaymentRepository payments,
                           BillingWebhookRepository webhookEvents, BillingProviderEventRepository providerEvents,
                           BillingService billing,
                           ProviderPaymentConfirmationService confirmations, String payloadHash) { }
}
