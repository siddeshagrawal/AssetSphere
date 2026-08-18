package com.assetsphere.modules.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetsphere.modules.billing.api.PaymentGateway;
import com.assetsphere.modules.billing.api.PaymentProvider;
import com.assetsphere.modules.billing.api.ProviderSubscriptionState;
import com.assetsphere.modules.billing.api.ProviderSubscriptionStatus;
import com.assetsphere.modules.billing.domain.Subscription;
import com.assetsphere.modules.billing.persistence.BillingProviderEventRepository;
import com.assetsphere.modules.billing.persistence.SubscriptionRepository;
import com.assetsphere.modules.common.exception.ConflictException;
import com.assetsphere.modules.common.exception.ServiceUnavailableException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class StripeSubscriptionReconciliationServiceTests {
    private static final Instant FREE_START = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant FREE_END = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant STRIPE_START = Instant.parse("2026-08-18T13:05:02Z");
    private static final Instant STRIPE_END = Instant.parse("2026-09-18T13:05:02Z");

    @Test
    void historicalActiveStripeSubscriptionReconcilesAuthoritativePeriodOnce() {
        Fixture fixture = fixture(staleStripeSubscription());
        when(fixture.gateway.subscriptionState("sub_123")).thenReturn(Optional.of(
                new ProviderSubscriptionState("sub_123", STRIPE_START, STRIPE_END, false,
                        ProviderSubscriptionStatus.ACTIVE)));
        when(fixture.providerEvents.markReconciled(PaymentProvider.STRIPE,
                "sub_123", "legacy-period-reconciliation")).thenReturn(true);

        fixture.service.reconcileLegacyIfNeeded(fixture.workspaceId);

        verify(fixture.billing).synchronizeStripeSubscription(fixture.workspaceId, "sub_123",
                STRIPE_START, STRIPE_END, false, ProviderSubscriptionStatus.ACTIVE);
    }

    @Test
    void checkoutSynchronizedSubscriptionDoesNotCallStripeAgain() {
        Fixture fixture = fixture(staleStripeSubscription());
        when(fixture.providerEvents.reconciled(PaymentProvider.STRIPE, "sub_123")).thenReturn(true);

        fixture.service.reconcileLegacyIfNeeded(fixture.workspaceId);

        verify(fixture.gateway, never()).subscriptionState("sub_123");
    }

    @Test
    void completedLegacyReconciliationDoesNotCallStripeAgain() {
        Fixture fixture = fixture(staleStripeSubscription());
        when(fixture.providerEvents.reconciled(PaymentProvider.STRIPE, "sub_123")).thenReturn(true);

        fixture.service.reconcileLegacyIfNeeded(fixture.workspaceId);

        verify(fixture.gateway, never()).subscriptionState("sub_123");
    }

    @Test
    void freeAndLocalSubscriptionsNeverContactStripe() {
        Subscription free = Subscription.free(UUID.randomUUID(), FREE_START, FREE_END);
        Fixture freeFixture = fixture(free);
        freeFixture.service.reconcileLegacyIfNeeded(freeFixture.workspaceId);
        verify(freeFixture.gateway, never()).subscriptionState(org.mockito.ArgumentMatchers.anyString());

        Subscription local = Subscription.free(UUID.randomUUID(), FREE_START, FREE_END);
        local.activatePro(PaymentProvider.RAZORPAY_LOCAL.name(), "payment_123", FREE_START, FREE_END);
        Fixture localFixture = fixture(local);
        localFixture.service.reconcileLegacyIfNeeded(localFixture.workspaceId);
        verify(localFixture.gateway, never()).subscriptionState(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void providerFailureLeavesExistingPeriodUntouchedAndRetryable() {
        Subscription subscription = staleStripeSubscription();
        Fixture fixture = fixture(subscription);
        when(fixture.gateway.subscriptionState("sub_123"))
                .thenThrow(new ServiceUnavailableException("Stripe unavailable", null));

        assertThatThrownBy(() -> fixture.service.reconcileLegacyIfNeeded(fixture.workspaceId))
                .isInstanceOf(ServiceUnavailableException.class);

        assertThat(subscription.getCurrentPeriodStart()).isEqualTo(FREE_START);
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(FREE_END);
        verify(fixture.providerEvents, never()).markReconciled(PaymentProvider.STRIPE,
                "sub_123", "legacy-period-reconciliation");
    }

    @Test
    void providerIdentityMismatchCannotCrossWorkspaceBoundary() {
        Fixture fixture = fixture(staleStripeSubscription());
        when(fixture.gateway.subscriptionState("sub_123")).thenReturn(Optional.of(
                new ProviderSubscriptionState("sub_other", STRIPE_START, STRIPE_END, false,
                        ProviderSubscriptionStatus.ACTIVE)));

        assertThatThrownBy(() -> fixture.service.reconcileLegacyIfNeeded(fixture.workspaceId))
                .isInstanceOf(ConflictException.class);

        verify(fixture.billing, never()).synchronizeStripeSubscription(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reconciliationPreservesProviderCancellationState() {
        Fixture fixture = fixture(staleStripeSubscription());
        when(fixture.gateway.subscriptionState("sub_123")).thenReturn(Optional.of(
                new ProviderSubscriptionState("sub_123", STRIPE_START, STRIPE_END, true,
                        ProviderSubscriptionStatus.ACTIVE)));
        when(fixture.providerEvents.markReconciled(PaymentProvider.STRIPE,
                "sub_123", "legacy-period-reconciliation")).thenReturn(true);

        fixture.service.reconcileLegacyIfNeeded(fixture.workspaceId);

        verify(fixture.billing).synchronizeStripeSubscription(fixture.workspaceId, "sub_123",
                STRIPE_START, STRIPE_END, true, ProviderSubscriptionStatus.ACTIVE);
    }

    @Test
    void newerLifecycleEvidencePreventsLegacyReconciliation() {
        Fixture fixture = fixture(staleStripeSubscription());
        when(fixture.providerEvents.reconciled(PaymentProvider.STRIPE, "sub_123")).thenReturn(true);

        fixture.service.reconcileLegacyIfNeeded(fixture.workspaceId);

        verify(fixture.gateway, never()).subscriptionState("sub_123");
        verify(fixture.billing, never()).synchronizeStripeSubscription(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.any());
    }

    private Subscription staleStripeSubscription() {
        Subscription subscription = Subscription.free(UUID.randomUUID(), FREE_START, FREE_END);
        subscription.activateProAwaitingProviderPeriod(PaymentProvider.STRIPE.name(), "sub_123",
                Instant.parse("2026-08-18T13:05:05Z"));
        return subscription;
    }

    @SuppressWarnings("unchecked")
    private Fixture fixture(Subscription subscription) {
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        BillingProviderEventRepository providerEvents = mock(BillingProviderEventRepository.class);
        ObjectProvider<PaymentGateway> gateways = mock(ObjectProvider.class);
        PaymentGateway gateway = mock(PaymentGateway.class);
        BillingService billing = mock(BillingService.class);
        when(subscriptions.findLockedByWorkspaceId(subscription.getWorkspaceId()))
                .thenReturn(Optional.of(subscription));
        when(gateways.orderedStream()).thenReturn(Stream.of(gateway));
        when(gateway.provider()).thenReturn(PaymentProvider.STRIPE);
        return new Fixture(new StripeSubscriptionReconciliationService(
                subscriptions, providerEvents, gateways, billing), subscription.getWorkspaceId(),
                providerEvents, gateway, billing);
    }

    private record Fixture(StripeSubscriptionReconciliationService service,
                           UUID workspaceId,
                           BillingProviderEventRepository providerEvents,
                           PaymentGateway gateway,
                           BillingService billing) { }
}