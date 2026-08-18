package com.assetsphere.modules.billing.application;

import com.assetsphere.modules.billing.api.PaymentGateway;
import com.assetsphere.modules.billing.api.PaymentProvider;
import com.assetsphere.modules.billing.api.Plan;
import com.assetsphere.modules.billing.api.SubscriptionStatus;
import com.assetsphere.modules.billing.persistence.BillingProviderEventRepository;
import com.assetsphere.modules.billing.persistence.SubscriptionRepository;
import com.assetsphere.modules.common.exception.ConflictException;
import com.assetsphere.modules.common.exception.ServiceUnavailableException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StripeSubscriptionReconciliationService {
    private static final String RECONCILIATION_EVENT_ID = "legacy-period-reconciliation";

    private final SubscriptionRepository subscriptions;
    private final BillingProviderEventRepository providerEvents;
    private final ObjectProvider<PaymentGateway> paymentGateways;
    private final BillingService billing;

    @Transactional
    public void reconcileLegacyIfNeeded(UUID workspaceId) {
        var subscription = subscriptions.findLockedByWorkspaceId(workspaceId).orElse(null);
        if (subscription == null
                || subscription.getPlan() != Plan.PRO
                || subscription.getStatus() != SubscriptionStatus.ACTIVE
                || !PaymentProvider.STRIPE.name().equals(subscription.getPaymentProvider())
                || !subscription.hasExternalSubscriptionIdentity()) {
            return;
        }

        String externalSubscriptionId = subscription.getExternalSubscriptionId();
        if (providerEvents.reconciled(PaymentProvider.STRIPE, externalSubscriptionId)) return;

        PaymentGateway gateway = paymentGateways.orderedStream()
                .filter(candidate -> candidate.provider() == PaymentProvider.STRIPE)
                .findFirst()
                .orElseThrow(() -> new ServiceUnavailableException("Stripe reconciliation is unavailable", null));
        var state = gateway.subscriptionState(externalSubscriptionId).orElseThrow(() ->
                new ServiceUnavailableException("Stripe subscription state is not available yet", null));
        if (!externalSubscriptionId.equals(state.externalSubscriptionId())) {
            throw new ConflictException("Stripe subscription relationship is inconsistent");
        }
        if (!providerEvents.markReconciled(PaymentProvider.STRIPE,
                externalSubscriptionId, RECONCILIATION_EVENT_ID)) return;
        billing.synchronizeStripeSubscription(workspaceId, externalSubscriptionId,
                state.periodStart(), state.periodEnd(), state.cancelAtPeriodEnd(), state.status());
    }
}
