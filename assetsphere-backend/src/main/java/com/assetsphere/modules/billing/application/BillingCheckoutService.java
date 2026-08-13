package com.assetsphere.modules.billing.application;

import com.assetsphere.modules.billing.api.CheckoutRequest;
import com.assetsphere.modules.billing.api.PaymentGateway;
import com.assetsphere.modules.billing.api.PaymentStatus;
import com.assetsphere.modules.billing.api.Plan;
import com.assetsphere.modules.billing.api.dto.response.CheckoutResponse;
import com.assetsphere.modules.billing.domain.BillingPayment;
import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.common.exception.InvalidRequestException;
import com.assetsphere.modules.common.exception.ServiceUnavailableException;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BillingCheckoutService {
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private final BillingPaymentTransaction paymentTransaction;
    private final BillingPaymentProperties properties;
    private final ObjectProvider<PaymentGateway> paymentGateways;
    private final BillingService billing;

    public CheckoutResponse checkout(UUID workspaceId, UUID userId, String idempotencyKey) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new InvalidRequestException("Idempotency-Key must contain 1 to 128 safe characters");
        }
        if (billing.currentPlan(workspaceId) != Plan.FREE) {
            throw new BusinessRuleViolationException("This workspace already has a paid plan");
        }
        PaymentGateway gateway = paymentGateways.orderedStream().findFirst()
                .orElseThrow(() -> new ServiceUnavailableException("Payments are not configured", null));
        if (!gateway.available()) {
            throw new ServiceUnavailableException("The selected payment provider is not configured", null);
        }
        var reservation = paymentTransaction.reserve(workspaceId, userId, idempotencyKey, gateway,
                properties.getProPriceMinor(), properties.getCurrency());
        BillingPayment payment = reservation.payment();
        if (payment.getProvider() != gateway.provider()) {
            gateway = paymentGateways.orderedStream().filter(candidate -> candidate.provider() == payment.getProvider())
                    .findFirst().orElseThrow(() -> new ServiceUnavailableException("The original payment provider is not configured", null));
        }
        if (payment.getProviderOrderId() != null) return response(payment, gateway);
        if (payment.getStatus() == PaymentStatus.FAILED) {
            throw new ServiceUnavailableException("The previous checkout attempt failed; use a new idempotency key", null);
        }
        if (!reservation.created()) {
            throw new ServiceUnavailableException("Checkout initialization is still in progress", null);
        }
        try {
            var session = gateway.createCheckout(new CheckoutRequest(workspaceId, userId, Plan.PRO,
                    payment.getAmountMinor(), payment.getCurrency(), payment.getReceipt()));
            BillingPayment updated = paymentTransaction.markOrderCreated(payment.getId(), session.providerOrderId(), session.checkoutUrl());
            return response(updated, gateway, session.providerOrderStatus());
        } catch (ServiceUnavailableException exception) {
            paymentTransaction.markFailed(payment.getId());
            throw exception;
        } catch (RuntimeException exception) {
            paymentTransaction.markFailed(payment.getId());
            throw new ServiceUnavailableException("Payment checkout is temporarily unavailable", exception);
        }
    }

    private CheckoutResponse response(BillingPayment payment, PaymentGateway gateway) {
        return response(payment, gateway, gateway.supportsHostedCheckout() ? null : payment.getStatus().name());
    }

    private CheckoutResponse response(BillingPayment payment, PaymentGateway gateway, String providerOrderStatus) {
            return new CheckoutResponse(gateway.provider(), gateway.clientKeyId(), payment.getProviderOrderId(),
                payment.getProviderCheckoutUrl(), payment.getProviderPaymentId(), gateway.supportsHostedCheckout(),
                providerOrderStatus,
                payment.getAmountMinor(), payment.getCurrency(), payment.getStatus());
    }
}
