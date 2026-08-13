package com.assetsphere.modules.billing.application;

import com.assetsphere.modules.billing.api.PaymentGateway;
import com.assetsphere.modules.billing.api.LocalPaymentDemoGateway;
import com.assetsphere.modules.billing.api.LocalPaymentMethod;
import com.assetsphere.modules.billing.api.PaymentStatus;
import com.assetsphere.modules.billing.domain.BillingPayment;
import com.assetsphere.modules.billing.persistence.BillingPaymentRepository;
import com.assetsphere.modules.billing.persistence.SubscriptionRepository;
import com.assetsphere.modules.common.exception.ConflictException;
import com.assetsphere.modules.common.exception.ResourceNotFoundException;
import com.assetsphere.modules.common.time.ClockProvider;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import com.assetsphere.modules.billing.api.PaymentProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class BillingPaymentTransaction {
    private final BillingPaymentRepository payments;
    private final SubscriptionRepository subscriptions;
    private final BillingPaymentProperties properties;
    private final ClockProvider clock;

    @Transactional
    PaymentReservation reserve(UUID workspaceId, UUID userId, String idempotencyKey, PaymentGateway gateway,
                               long amountMinor, String currency) {
        subscriptions.findLockedByWorkspaceId(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace subscription was not found"));
        Optional<BillingPayment> replay = payments.findByWorkspaceIdAndIdempotencyKey(workspaceId, idempotencyKey);
        if (replay.isPresent()) return new PaymentReservation(replay.get(), false);
        Optional<BillingPayment> pending = payments
                .findFirstByWorkspaceIdAndProviderAndStatusInAndCreatedAtAfterOrderByCreatedAtDesc(
                        workspaceId, gateway.provider(), List.of(PaymentStatus.CREATED, PaymentStatus.ORDER_CREATED),
                        clock.now().minus(properties.getPendingCheckoutWindow()));
        if (pending.isPresent()) return new PaymentReservation(pending.get(), false);
        UUID id = UUID.randomUUID();
        String receipt = "as_" + id.toString().replace("-", "");
        boolean created = payments.createIfAbsent(id, workspaceId, userId, gateway.provider().name(), idempotencyKey,
                receipt, amountMinor, currency) == 1;
        return new PaymentReservation(
                payments.findByWorkspaceIdAndIdempotencyKey(workspaceId, idempotencyKey).orElseThrow(), created);
    }

    @Transactional
    BillingPayment markOrderCreated(UUID paymentId, String providerOrderId, String checkoutUrl) {
        BillingPayment payment = payments.findById(paymentId).orElseThrow();
        if (payment.getStatus() == PaymentStatus.CREATED) payment.orderCreated(providerOrderId, checkoutUrl);
        return payment;
    }

    @Transactional
    void markFailed(UUID paymentId) {
        BillingPayment payment = payments.findById(paymentId).orElseThrow();
        if (payment.getStatus() == PaymentStatus.CREATED) payment.failed("PROVIDER_UNAVAILABLE", null);
    }

    @Transactional
    void recordProviderPayment(UUID paymentId, String providerPaymentId) {
        BillingPayment payment = payments.findLockedById(paymentId).orElseThrow();
        if (payment.hasProviderPaymentIdentity() && !payment.getProviderPaymentId().equals(providerPaymentId)) {
            throw new ConflictException("This payment order already has a provider payment");
        }
        payment.providerPaymentCreated(providerPaymentId);
    }

    @Transactional
    LocalPaymentInitiation initiateLocalPayment(UUID paymentId, LocalPaymentDemoGateway gateway,
                                                LocalPaymentMethod method, Map<String, String> details,
                                                String idempotencyKey) {
        BillingPayment payment = payments.findLockedById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Billing payment was not found"));
        if (payment.hasProviderPaymentIdentity()) {
            return new LocalPaymentInitiation(payment.getProviderPaymentId(), null, false);
        }
        if (payment.getStatus() != PaymentStatus.ORDER_CREATED || payment.getProviderOrderId() == null) {
            throw new ConflictException("This payment attempt cannot initiate a provider payment");
        }
        var result = gateway.create(payment.getProviderOrderId(), method, details, idempotencyKey);
        payment.providerPaymentCreated(result.paymentId());
        return new LocalPaymentInitiation(result.paymentId(), result, true);
    }

    record PaymentReservation(BillingPayment payment, boolean created) { }
    record LocalPaymentInitiation(String paymentId, LocalPaymentDemoGateway.LocalPaymentResult result,
                                  boolean created) { }
}
