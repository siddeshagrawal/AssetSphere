package com.assetsphere.modules.billing.application;

import com.assetsphere.modules.billing.api.PaymentProvider;
import com.assetsphere.modules.billing.api.PaymentStatus;
import com.assetsphere.modules.billing.api.Plan;
import com.assetsphere.modules.billing.domain.BillingPayment;
import com.assetsphere.modules.billing.persistence.BillingPaymentRepository;
import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.common.exception.ConflictException;
import com.assetsphere.modules.common.exception.ResourceNotFoundException;
import com.assetsphere.modules.common.time.ClockProvider;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProviderPaymentConfirmationService {
    private final BillingPaymentRepository payments;
    private final BillingService billing;
    private final ClockProvider clock;

    @Transactional
    public void succeeded(PaymentProvider provider, String orderId, String paymentId,
                          long amountMinor, String currency) {
        succeeded(provider, orderId, paymentId, amountMinor, currency, null, null);
    }

    @Transactional
    public void succeeded(PaymentProvider provider, String orderId, String paymentId,
                          long amountMinor, String currency, Instant periodStart, Instant periodEnd) {
        if (provider == PaymentProvider.STRIPE && (paymentId == null || !paymentId.startsWith("sub_"))) {
            throw new BusinessRuleViolationException("Stripe subscription confirmation is missing its subscription identity");
        }
        BillingPayment payment = payment(provider, orderId);
        validate(paymentId, amountMinor, currency, payment);
        if (payment.getStatus() == PaymentStatus.PAID) {
            if (!payment.hasProviderPaymentIdentity()) {
                if (provider != PaymentProvider.STRIPE) inconsistent();
                billing.recoverStripeSubscriptionIdentity(payment.getWorkspaceId(), paymentId,
                        periodStart, periodEnd);
                payment.recoverPaidProviderIdentity(paymentId);
                return;
            }
            if (!paymentId.equals(payment.getProviderPaymentId())) {
                throw new ConflictException("Payment already belongs to a different provider subscription");
            }
            return;
        }
        if (payment.getStatus() == PaymentStatus.FAILED || payment.getStatus() == PaymentStatus.CANCELED) inconsistent();
        if (payment.getRequestedPlan() != Plan.PRO) inconsistent();
        billing.activatePro(payment.getWorkspaceId(), provider, paymentId, periodStart, periodEnd);
        payment.paid(paymentId, clock.now());
    }

    @Transactional
    public void failed(PaymentProvider provider, String orderId, String paymentId,
                       long amountMinor, String currency) {
        failed(provider, orderId, paymentId, amountMinor, currency, "PAYMENT_FAILED");
    }

    @Transactional
    public void failed(PaymentProvider provider, String orderId, String paymentId,
                       long amountMinor, String currency, String failureCode) {
        BillingPayment payment = payment(provider, orderId);
        validate(paymentId, amountMinor, currency, payment);
        if (payment.getStatus() != PaymentStatus.PAID && payment.getStatus() != PaymentStatus.CANCELED) {
            payment.providerPaymentCreated(paymentId);
            payment.failed(failureCode, clock.now());
        }
    }

    @Transactional
    public void canceled(PaymentProvider provider, String orderId, String paymentId,
                         long amountMinor, String currency) {
        BillingPayment payment = payment(provider, orderId);
        validate(paymentId, amountMinor, currency, payment);
        if (payment.getStatus() != PaymentStatus.PAID) {
            payment.providerPaymentCreated(paymentId);
            payment.canceled(clock.now());
        }
    }

    private BillingPayment payment(PaymentProvider provider, String orderId) {
        return payments.findLockedByProviderAndProviderOrderId(provider, orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment order was not found"));
    }

    private void validate(String paymentId, long amountMinor, String currency, BillingPayment payment) {
        if (paymentId == null || paymentId.isBlank()
                || payment.getAmountMinor() != amountMinor
                || currency == null || !payment.getCurrency().equalsIgnoreCase(currency)) inconsistent();
        if (payment.getStatus() != PaymentStatus.PAID && payment.hasProviderPaymentIdentity()
                && !paymentId.equals(payment.getProviderPaymentId())) inconsistent();
    }

    private void inconsistent() {
        throw new BusinessRuleViolationException("Provider payment confirmation is inconsistent");
    }
}
