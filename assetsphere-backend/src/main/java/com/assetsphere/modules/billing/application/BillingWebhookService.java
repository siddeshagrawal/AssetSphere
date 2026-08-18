package com.assetsphere.modules.billing.application;

import com.assetsphere.modules.billing.api.PaymentGateway;
import com.assetsphere.modules.billing.api.PaymentProvider;
import com.assetsphere.modules.billing.api.PaymentWebhookStatus;
import com.assetsphere.modules.billing.persistence.BillingPaymentRepository;
import com.assetsphere.modules.billing.persistence.BillingProviderEventRepository;
import com.assetsphere.modules.billing.persistence.BillingWebhookRepository;
import com.assetsphere.modules.common.exception.ServiceUnavailableException;
import com.assetsphere.modules.common.time.ClockProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BillingWebhookService {
    private final ObjectProvider<PaymentGateway> paymentGateways;
    private final BillingPaymentRepository payments;
    private final BillingWebhookRepository webhookEvents;
    private final BillingProviderEventRepository providerEvents;
    private final BillingService billing;
    private final ProviderPaymentConfirmationService confirmations;
    private final ClockProvider clock;

    @Transactional
    public void handle(PaymentProvider provider, String providerEventId, String payload, String signature) {
        PaymentGateway gateway = paymentGateways.orderedStream().filter(candidate -> candidate.provider() == provider).findFirst()
                .orElseThrow(() -> new ServiceUnavailableException("Payments are not configured", null));
        String payloadHash = sha256(payload);
        String eventId = providerEventId == null || providerEventId.isBlank() || providerEventId.length() > 255
                ? payloadHash : providerEventId;
        var event = gateway.verifyWebhook(eventId, payload, signature);
        if (!event.verified()) throw new IllegalStateException("Payment gateway returned an unverified webhook");
        if (!webhookEvents.claim(event.provider(), event.eventId(), event.eventType(), payloadHash, clock.now())) return;
        if (requiresStripeSubscriptionIdentity(event)
                && (event.providerPaymentId() == null || event.providerPaymentId().isBlank())) {
            throw new ServiceUnavailableException("Stripe subscription identity is not available yet", null);
        }
        String providerIdentity = providerIdentity(event);
        if (providerIdentity != null && !providerEvents.accept(event.provider(), providerIdentity,
                event.occurredAt(), eventPriority(event), event.eventId())) {
            webhookEvents.complete(event.provider(), event.eventId(), false, clock.now());
            return;
        }
        boolean processed = false;
        if (event.providerOrderId() != null) {
            var payment = payments.findLockedByProviderAndProviderOrderId(
                    event.provider(), event.providerOrderId()).orElse(null);
            if (payment != null && "ORDER_CREATED".equals(event.eventType())) {
                processed = true;
            } else if (payment != null && "PAYMENT_CREATED".equals(event.eventType())
                    && event.providerPaymentId() != null) {
                payment.providerPaymentCreated(event.providerPaymentId());
                processed = true;
            } else if (payment != null && event.status() == PaymentWebhookStatus.CANCELED
                    && payment.getStatus() != com.assetsphere.modules.billing.api.PaymentStatus.PAID) {
                payment.canceled(clock.now());
                processed = true;
            } else if (payment != null && event.status() == PaymentWebhookStatus.FAILED
                    && payment.getStatus() != com.assetsphere.modules.billing.api.PaymentStatus.PAID) {
                payment.failed("PAYMENT_FAILED", clock.now());
                processed = true;
            } else if (payment != null && event.status() == PaymentWebhookStatus.SUCCEEDED
                    && event.providerPaymentId() != null
                    && payment.getAmountMinor() == event.amountMinor()
                    && payment.getCurrency().equalsIgnoreCase(event.currency())) {
                confirmations.succeeded(event.provider(), event.providerOrderId(), event.providerPaymentId(),
                        event.amountMinor(), event.currency(), event.periodStart(), event.periodEnd());
                if (event.provider() == PaymentProvider.STRIPE) {
                    synchronizeConfirmedStripeSubscription(gateway, payment.getWorkspaceId(), event);
                }
                processed = true;
            }
        } else if (event.providerPaymentId() != null) {
            var payment = payments.findLockedByProviderAndProviderPaymentId(
                    event.provider(), event.providerPaymentId()).orElse(null);
            if (payment != null && payment.getStatus() == com.assetsphere.modules.billing.api.PaymentStatus.PAID) {
                if (isStripeSubscriptionSynchronizationEvent(event)) {
                    if (event.cancelAtPeriodEnd() != null && event.subscriptionStatus() != null) {
                        billing.synchronizeStripeSubscription(payment.getWorkspaceId(), event.providerPaymentId(),
                                event.periodStart(), event.periodEnd(), event.cancelAtPeriodEnd(),
                                event.subscriptionStatus());
                        processed = event.subscriptionStatus()
                                != com.assetsphere.modules.billing.api.ProviderSubscriptionStatus.UNKNOWN;
                        if (processed && validPeriod(event.periodStart(), event.periodEnd())) {
                            providerEvents.markReconciled(PaymentProvider.STRIPE,
                                    event.providerPaymentId(), event.eventId());
                        }
                    }
                } else if (event.status() == PaymentWebhookStatus.SUCCEEDED) {
                    if (event.eventType().startsWith("invoice.")
                            && (payment.getAmountMinor() != event.amountMinor()
                            || !payment.getCurrency().equalsIgnoreCase(event.currency()))) {
                        throw new com.assetsphere.modules.common.exception.ConflictException(
                                "Stripe invoice does not match the workspace subscription purchase");
                    }
                    billing.renewPaidPlan(payment.getWorkspaceId(), event.provider(), event.providerPaymentId());
                    processed = true;
                } else if (event.status() == PaymentWebhookStatus.CANCELED) {
                    billing.cancelPaidPlan(payment.getWorkspaceId(), event.provider(), event.providerPaymentId());
                    processed = true;
                } else if (event.status() == PaymentWebhookStatus.FAILED) {
                    billing.markPastDue(payment.getWorkspaceId(), event.provider(), event.providerPaymentId());
                    processed = true;
                }
            }
            if (payment == null && event.provider() == PaymentProvider.STRIPE
                    && (event.eventType().startsWith("invoice.")
                    || event.eventType().startsWith("customer.subscription."))) {
                throw new ServiceUnavailableException("Stripe subscription relationship is not available yet", null);
            }
        }
        webhookEvents.complete(event.provider(), event.eventId(), processed, clock.now());
    }

    private void synchronizeConfirmedStripeSubscription(
            PaymentGateway gateway,
            java.util.UUID workspaceId,
            com.assetsphere.modules.billing.api.PaymentWebhookEvent event) {
        var state = gateway.subscriptionState(event.providerPaymentId()).orElseThrow(() ->
                new ServiceUnavailableException("Stripe subscription state is not available yet", null));
        if (!event.providerPaymentId().equals(state.externalSubscriptionId())) {
            throw new com.assetsphere.modules.common.exception.ConflictException(
                    "Stripe subscription relationship is inconsistent");
        }
        if (!providerEvents.accept(PaymentProvider.STRIPE, "SUBSCRIPTION:" + state.externalSubscriptionId(),
                event.occurredAt(), 345, event.eventId())) {
            return;
        }
        billing.synchronizeStripeSubscription(workspaceId, state.externalSubscriptionId(),
                state.periodStart(), state.periodEnd(), state.cancelAtPeriodEnd(), state.status());
        providerEvents.markReconciled(PaymentProvider.STRIPE, state.externalSubscriptionId(), event.eventId());
    }

    private boolean requiresStripeSubscriptionIdentity(com.assetsphere.modules.billing.api.PaymentWebhookEvent event) {
        if (event.provider() != PaymentProvider.STRIPE) return false;
        return (event.eventType().startsWith("checkout.session.") && event.status() == PaymentWebhookStatus.SUCCEEDED)
                || (event.eventType().startsWith("invoice.") && event.status() != PaymentWebhookStatus.IGNORED)
                || isStripeSubscriptionSynchronizationEvent(event)
                || "customer.subscription.deleted".equals(event.eventType());
    }

    private String providerIdentity(com.assetsphere.modules.billing.api.PaymentWebhookEvent event) {
        if (event.provider() != PaymentProvider.STRIPE) return null;
        if (isStripeSubscriptionSynchronizationEvent(event)
                && event.subscriptionStatus()
                == com.assetsphere.modules.billing.api.ProviderSubscriptionStatus.UNKNOWN) return null;
        if (event.providerOrderId() != null) return "ORDER:" + event.providerOrderId();
        if (event.providerPaymentId() != null) return "SUBSCRIPTION:" + event.providerPaymentId();
        return null;
    }

    private int eventPriority(com.assetsphere.modules.billing.api.PaymentWebhookEvent event) {
        if (event.providerOrderId() != null) {
            return switch (event.status()) {
                case SUCCEEDED -> 400;
                case FAILED -> 300;
                case CANCELED -> 200;
                case IGNORED -> 100;
            };
        }
        if ("customer.subscription.deleted".equals(event.eventType())
                || event.subscriptionStatus() != null && event.subscriptionStatus().terminal()) return 500;
        if (event.subscriptionStatus() != null && !event.subscriptionStatus().entitled()
                && event.subscriptionStatus()
                != com.assetsphere.modules.billing.api.ProviderSubscriptionStatus.UNKNOWN) return 450;
        if ("customer.subscription.updated".equals(event.eventType())
                && event.subscriptionStatus() != null && event.subscriptionStatus().entitled()) return 350;
        if ("customer.subscription.created".equals(event.eventType())
                && event.subscriptionStatus() != null && event.subscriptionStatus().entitled()) return 340;
        return switch (event.status()) {
            case FAILED -> 400;
            case SUCCEEDED -> 300;
            case CANCELED -> 500;
            case IGNORED -> 200;
        };
    }

    private boolean isStripeSubscriptionSynchronizationEvent(
            com.assetsphere.modules.billing.api.PaymentWebhookEvent event) {
        return event.provider() == PaymentProvider.STRIPE
                && ("customer.subscription.created".equals(event.eventType())
                || "customer.subscription.updated".equals(event.eventType()));
    }

    private boolean validPeriod(java.time.Instant start, java.time.Instant end) {
        return start != null && end != null && end.isAfter(start);
    }

    private String sha256(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
