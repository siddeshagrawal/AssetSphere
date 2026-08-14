package com.assetsphere.modules.billing.application;

import com.assetsphere.modules.billing.api.BillingEntitlementFacade;
import com.assetsphere.modules.billing.api.BillingProperties;
import com.assetsphere.modules.billing.api.Plan;
import com.assetsphere.modules.billing.api.PaymentProvider;
import com.assetsphere.modules.billing.api.PaymentStatus;
import com.assetsphere.modules.billing.api.PlanEntitlements;
import com.assetsphere.modules.billing.api.ProviderSubscriptionStatus;
import com.assetsphere.modules.billing.api.UsageMetric;
import com.assetsphere.modules.billing.api.WorkspaceResourceUsageProvider;
import com.assetsphere.modules.billing.api.WorkspacePlanProvider;
import com.assetsphere.modules.billing.api.dto.response.BillingResponse;
import com.assetsphere.modules.billing.api.dto.response.BillingUsageResponse;
import com.assetsphere.modules.billing.api.dto.response.PlanResponse;
import com.assetsphere.modules.billing.domain.Subscription;
import com.assetsphere.modules.billing.persistence.BillingUsageRepository;
import com.assetsphere.modules.billing.persistence.BillingPaymentRepository;
import com.assetsphere.modules.billing.persistence.SubscriptionRepository;
import com.assetsphere.modules.common.exception.QuotaExceededException;
import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.common.exception.ConflictException;
import com.assetsphere.modules.common.time.ClockProvider;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BillingService implements BillingEntitlementFacade, WorkspacePlanProvider {
    private static final UUID SYSTEM_ACTOR_ID = new UUID(0L, 0L);
    private final SubscriptionRepository subscriptions;
    private final BillingUsageRepository usageRepository;
    private final BillingPaymentRepository paymentRepository;
    private final BillingProperties properties;
    private final BillingPaymentProperties paymentProperties;
    private final ClockProvider clock;
    private final ObjectProvider<WorkspaceResourceUsageProvider> resourceUsageProviders;
    private final ObjectProvider<com.assetsphere.modules.billing.api.PaymentGateway> paymentGateways;

    @Override
    @Transactional
    public PlanEntitlements entitlements(UUID workspaceId) {
        return properties.entitlements(effectivePlan(subscription(workspaceId)));
    }

    @Override
    @Transactional
    public void requireAssetUpload(UUID workspaceId, long additionalBytes) {
        Subscription subscription = lockedSubscription(workspaceId);
        PlanEntitlements entitlements = properties.entitlements(effectivePlan(subscription));
        var usage = resourceUsage();
        var current = usage.usage(workspaceId);
        if (current.assets() >= entitlements.maxAssets()) {
            throw new QuotaExceededException("You've used " + current.assets() + "/" + entitlements.maxAssets() + " assets in this workspace.");
        }
        requireStorage(entitlements, current.storageBytes(), additionalBytes);
    }

    @Override
    @Transactional
    public void requireStorage(UUID workspaceId, long additionalBytes) {
        Subscription subscription = lockedSubscription(workspaceId);
        PlanEntitlements entitlements = properties.entitlements(effectivePlan(subscription));
        requireStorage(entitlements, resourceUsage().usage(workspaceId).storageBytes(), additionalBytes);
    }

    private void requireStorage(PlanEntitlements entitlements, long currentBytes, long additionalBytes) {
        if (currentBytes + additionalBytes > entitlements.maxStorageBytes()) {
            throw new QuotaExceededException("This upload would exceed the workspace storage allowance.");
        }
    }

    @Override
    @Transactional
    public void requireAvailable(UUID workspaceId, UsageMetric metric) {
        Subscription subscription = subscription(workspaceId);
        long limit = properties.entitlements(effectivePlan(subscription)).limit(metric);
        long used = usageRepository.findUsage(workspaceId, periodStart(subscription)).getOrDefault(metric, 0L);
        if (used >= limit) throw new QuotaExceededException(quotaMessage(metric, limit));
    }

    @Override
    @Transactional
    public long consume(UUID workspaceId, UsageMetric metric) {
        Subscription subscription = subscription(workspaceId);
        long limit = properties.entitlements(effectivePlan(subscription)).limit(metric);
        if (limit <= 0) throw new QuotaExceededException(quotaMessage(metric, limit));
        long consumed = usageRepository.incrementWithinLimit(workspaceId, metric, periodStart(subscription), limit);
        if (consumed < 0) throw new QuotaExceededException(quotaMessage(metric, limit));
        return consumed;
    }

    @Override
    @Transactional
    public long consumeOnce(UUID workspaceId, UsageMetric metric, UUID operationId) {
        Subscription subscription = subscription(workspaceId);
        long limit = properties.entitlements(effectivePlan(subscription)).limit(metric);
        if (limit <= 0) throw new QuotaExceededException(quotaMessage(metric, limit));
        long consumed = usageRepository.incrementOnceWithinLimit(
                workspaceId, metric, operationId, periodStart(subscription), limit);
        if (consumed < 0) throw new QuotaExceededException(quotaMessage(metric, limit));
        return consumed;
    }

    @Transactional
    public BillingResponse billing(UUID workspaceId) {
        Subscription subscription = subscription(workspaceId);
        PlanEntitlements entitlements = properties.entitlements(effectivePlan(subscription));
        var monthly = usageRepository.findUsage(workspaceId, periodStart(subscription));
        var resources = resourceUsage().usage(workspaceId);
        BillingUsageResponse usage = new BillingUsageResponse(resources.assets(), resources.storageBytes(),
                monthly.getOrDefault(UsageMetric.AI_INSIGHT, 0L), monthly.getOrDefault(UsageMetric.ASK, 0L),
                monthly.getOrDefault(UsageMetric.EVOLUTION, 0L), monthly.getOrDefault(UsageMetric.QUIZ_GENERATION, 0L));
        BillingUsageResponse remaining = new BillingUsageResponse(
                remaining(entitlements.maxAssets(), usage.assets()), remaining(entitlements.maxStorageBytes(), usage.storageBytes()),
                remaining(entitlements.monthlyAiInsights(), usage.aiInsights()),
                remaining(entitlements.monthlyAskRequests(), usage.askRequests()),
                remaining(entitlements.monthlyEvolutionComparisons(), usage.evolutionComparisons()),
                remaining(entitlements.monthlyQuizGenerations(), usage.quizGenerations()));
        PaymentProvider provider = provider(subscription);
        Optional<com.assetsphere.modules.billing.domain.BillingPayment> relevantPayment =
                activePro(subscription) && provider != null && subscription.hasExternalSubscriptionIdentity()
                ? paymentRepository.findByProviderAndProviderPaymentId(provider, subscription.getExternalSubscriptionId())
                : currentAttempt(workspaceId);
        return new BillingResponse(subscription.getPlan(), subscription.getStatus(), entitlements, usage, remaining,
                subscription.getCurrentPeriodStart(), subscription.getCurrentPeriodEnd(),
                relevantPayment.map(payment -> payment.getStatus()).orElse(null), provider,
                activePro(subscription) && provider == PaymentProvider.STRIPE && !subscription.isCancelAtPeriodEnd(),
                subscription.isCancelAtPeriodEnd());
    }

    public List<PlanResponse> plans() {
        return Arrays.stream(Plan.values()).map(plan -> new PlanResponse(plan, properties.entitlements(plan))).toList();
    }

    @Transactional
    @Override
    public Plan currentPlan(UUID workspaceId) {
        return effectivePlan(subscription(workspaceId));
    }

    @Transactional
    public void activatePro(UUID workspaceId, PaymentProvider provider, String externalPaymentId) {
        activatePro(workspaceId, provider, externalPaymentId, null, null);
    }

    @Transactional
    public void activatePro(UUID workspaceId, PaymentProvider provider, String externalPaymentId,
                            Instant authoritativeStart, Instant authoritativeEnd) {
        Subscription subscription = lockedSubscription(workspaceId);
        if (subscription.getPlan() == Plan.ENTERPRISE) {
            throw new BusinessRuleViolationException("Enterprise workspaces cannot be changed by payment checkout");
        }
        if (activePro(subscription)) {
            if (!subscription.matches(provider.name(), externalPaymentId)) {
                throw new ConflictException("Workspace already has a different active paid subscription");
            }
            subscription.synchronizePeriod(authoritativeStart, authoritativeEnd);
            return;
        }
        Instant now = clock.now();
        if (provider == PaymentProvider.STRIPE) {
            if (authoritativeStart == null && authoritativeEnd == null) {
                subscription.activateProAwaitingProviderPeriod(provider.name(), externalPaymentId, now);
                return;
            }
            if (!validPeriod(authoritativeStart, authoritativeEnd)) {
                throw new BusinessRuleViolationException("Stripe subscription period is invalid");
            }
        }
        Instant start = authoritativeStart == null ? now : authoritativeStart;
        Instant end = authoritativeEnd == null ? plusMonth(start) : authoritativeEnd;
        subscription.activatePro(provider.name(), externalPaymentId, start, end);
    }

    @Transactional
    void recoverStripeSubscriptionIdentity(UUID workspaceId, String externalSubscriptionId,
                                           Instant authoritativeStart, Instant authoritativeEnd) {
        if (externalSubscriptionId == null || !externalSubscriptionId.startsWith("sub_")) {
            throw new ConflictException("Stripe subscription identity is invalid");
        }
        Subscription subscription = lockedSubscription(workspaceId);
        if (!activePro(subscription) || !PaymentProvider.STRIPE.name().equals(subscription.getPaymentProvider())) {
            throw new ConflictException("Workspace does not have a recoverable Stripe subscription");
        }
        if (subscription.hasExternalSubscriptionIdentity()) {
            if (!subscription.getExternalSubscriptionId().equals(externalSubscriptionId)) {
                throw new ConflictException("Workspace already has a different active paid subscription");
            }
            subscription.synchronizePeriod(authoritativeStart, authoritativeEnd);
            return;
        }
        subscription.recoverMissingStripeSubscriptionId(externalSubscriptionId);
        subscription.synchronizePeriod(authoritativeStart, authoritativeEnd);
    }

    @Transactional
    public void renewPaidPlan(UUID workspaceId, PaymentProvider provider, String externalSubscriptionId) {
        renewPaidPlan(workspaceId, provider, externalSubscriptionId, null, null);
    }

    @Transactional
    public void renewPaidPlan(UUID workspaceId, PaymentProvider provider, String externalSubscriptionId,
                              Instant authoritativeStart, Instant authoritativeEnd) {
        Subscription subscription = lockedSubscription(workspaceId);
        if (!subscription.matches(provider.name(), externalSubscriptionId)) return;
        if (provider == PaymentProvider.STRIPE) {
            subscription.restorePaidEntitlement();
            return;
        }
        if (!subscription.isCancelAtPeriodEnd()) {
            if (authoritativeEnd != null) {
                subscription.synchronizePeriod(authoritativeStart, authoritativeEnd);
                return;
            }
            Instant base = clock.now().isAfter(subscription.getCurrentPeriodEnd())
                    ? clock.now() : subscription.getCurrentPeriodEnd();
            subscription.renew(plusMonth(base));
        }
    }

    @Transactional
    public void cancelAtPeriodEnd(UUID workspaceId) {
        Subscription subscription = subscription(workspaceId);
        PaymentProvider provider = provider(subscription);
        if (!activePro(subscription) || provider != PaymentProvider.STRIPE
                || !subscription.hasExternalSubscriptionIdentity()) {
            throw new com.assetsphere.modules.common.exception.BusinessRuleViolationException(
                    "Only an active Stripe subscription can be canceled");
        }
        var gateway = paymentGateways.orderedStream()
                .filter(candidate -> candidate.provider() == PaymentProvider.STRIPE && candidate.supportsCancellation())
                .findFirst().orElseThrow(() -> new IllegalStateException("Stripe cancellation is unavailable"));
        gateway.cancelAtPeriodEnd(subscription.getExternalSubscriptionId());
        subscription.scheduleCancellation();
    }

    @Transactional
    public void markPastDue(UUID workspaceId, PaymentProvider provider, String externalSubscriptionId) {
        Subscription subscription = lockedSubscription(workspaceId);
        if (subscription.matches(provider.name(), externalSubscriptionId)) subscription.markPastDue();
    }

    @Transactional
    public void cancelPaidPlan(UUID workspaceId, PaymentProvider provider, String externalSubscriptionId) {
        Subscription subscription = lockedSubscription(workspaceId);
        if (subscription.matches(provider.name(), externalSubscriptionId)) {
            Instant now = clock.now();
            subscription.cancel(monthStart(now), nextMonth(now));
        }
    }

    @Transactional
    public void synchronizeStripeSubscription(UUID workspaceId, String externalSubscriptionId,
                                               Instant periodStart, Instant periodEnd,
                                               boolean cancelAtPeriodEnd,
                                               ProviderSubscriptionStatus providerStatus) {
        Subscription subscription = lockedSubscription(workspaceId);
        if (!subscription.matches(PaymentProvider.STRIPE.name(), externalSubscriptionId)
                || providerStatus == null || providerStatus == ProviderSubscriptionStatus.UNKNOWN) {
            return;
        }
        if (providerStatus.terminal()) {
            Instant now = clock.now();
            subscription.cancel(monthStart(now), nextMonth(now));
        } else if (providerStatus.entitled()) {
            if (!validPeriod(periodStart, periodEnd)) return;
            subscription.synchronizePeriod(periodStart, periodEnd);
            subscription.synchronizeCancellation(cancelAtPeriodEnd);
        } else {
            if (validPeriod(periodStart, periodEnd)) {
                subscription.synchronizePeriodWithoutActivation(periodStart, periodEnd);
            }
            subscription.synchronizeCancellation(cancelAtPeriodEnd);
            subscription.markPastDue();
        }
    }

    private Subscription subscription(UUID workspaceId) {
        Instant now = clock.now();
        Subscription subscription = subscriptions.findByWorkspaceId(workspaceId).orElse(null);
        if (subscription == null) {
            subscriptions.createFreeIfAbsent(UUID.randomUUID(), workspaceId, monthStart(now), nextMonth(now),
                    SYSTEM_ACTOR_ID);
            subscription = subscriptions.findByWorkspaceId(workspaceId).orElseThrow();
        }
        if (!now.isBefore(subscription.getCurrentPeriodEnd())) {
            if (subscription.getPlan() == Plan.PRO) {
                if (provider(subscription) != PaymentProvider.STRIPE) {
                    subscription.expireToFree(monthStart(now), nextMonth(now));
                }
            } else {
                subscription.advancePeriod(monthStart(now), nextMonth(now));
            }
        }
        return subscription;
    }

    private Subscription lockedSubscription(UUID workspaceId) {
        subscription(workspaceId);
        return subscriptions.findLockedByWorkspaceId(workspaceId).orElseThrow();
    }

    private WorkspaceResourceUsageProvider resourceUsage() {
        return resourceUsageProviders.orderedStream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Workspace resource usage provider is not configured"));
    }

    private Instant periodStart(Subscription subscription) {
        return subscription.getUsagePeriodStart();
    }

    private Instant monthStart(Instant now) {
        return now.atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private Instant nextMonth(Instant now) {
        return now.atZone(ZoneOffset.UTC).toLocalDate().with(TemporalAdjusters.firstDayOfNextMonth())
                .atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private Instant plusMonth(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
    }

    private boolean validPeriod(Instant start, Instant end) {
        return start != null && end != null && end.isAfter(start);
    }

    private boolean activePro(Subscription subscription) {
        return subscription.getPlan() == Plan.PRO
                && subscription.getStatus() == com.assetsphere.modules.billing.api.SubscriptionStatus.ACTIVE;
    }

    private PaymentProvider provider(Subscription subscription) {
        if (subscription.getPaymentProvider() == null) return null;
        try { return PaymentProvider.valueOf(subscription.getPaymentProvider()); }
        catch (IllegalArgumentException exception) { return null; }
    }

    private Optional<com.assetsphere.modules.billing.domain.BillingPayment> currentAttempt(UUID workspaceId) {
        return paymentRepository.findFirstByWorkspaceIdOrderByCreatedAtDesc(workspaceId)
                .filter(payment -> ((payment.getStatus() == PaymentStatus.CREATED
                        || payment.getStatus() == PaymentStatus.ORDER_CREATED)
                        && payment.getCreatedAt() != null
                        && !payment.getCreatedAt().isBefore(
                                clock.now().minus(paymentProperties.getPendingCheckoutWindow())))
                        || ((payment.getStatus() == PaymentStatus.FAILED
                        || payment.getStatus() == PaymentStatus.CANCELED)
                        && payment.getCreatedAt() != null
                        && !payment.getCreatedAt().isBefore(
                                clock.now().minus(paymentProperties.getAttemptFeedbackWindow()))));
    }

    private long remaining(long limit, long used) {
        return Math.max(0, limit - used);
    }

    private Plan effectivePlan(Subscription subscription) {
        return subscription.getStatus() == com.assetsphere.modules.billing.api.SubscriptionStatus.ACTIVE
                ? subscription.getPlan() : Plan.FREE;
    }

    private String quotaMessage(UsageMetric metric, long limit) {
        String label = switch (metric) {
            case AI_INSIGHT -> "AI generations";
            case ASK -> "Ask requests";
            case EVOLUTION -> "Evolution comparisons";
            case QUIZ_GENERATION -> "Quiz generations";
        };
        return "You've used " + limit + "/" + limit + " " + label + " this month.";
    }
}
