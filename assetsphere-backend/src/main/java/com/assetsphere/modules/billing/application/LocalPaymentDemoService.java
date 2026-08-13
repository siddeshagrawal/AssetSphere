package com.assetsphere.modules.billing.application;

import com.assetsphere.modules.billing.api.LocalPaymentDemoGateway;
import com.assetsphere.modules.billing.api.LocalPaymentMethod;
import com.assetsphere.modules.billing.api.PaymentProvider;
import com.assetsphere.modules.billing.api.PaymentStatus;
import com.assetsphere.modules.billing.api.Plan;
import com.assetsphere.modules.billing.api.dto.request.LocalPaymentRequest;
import com.assetsphere.modules.billing.api.dto.response.LocalPaymentResponse;
import com.assetsphere.modules.billing.domain.BillingPayment;
import com.assetsphere.modules.billing.persistence.BillingPaymentRepository;
import com.assetsphere.modules.common.exception.AuthorizationDeniedException;
import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.common.exception.InvalidRequestException;
import com.assetsphere.modules.common.exception.ResourceNotFoundException;
import com.assetsphere.modules.common.exception.ServiceUnavailableException;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.time.Year;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LocalPaymentDemoService {
    private static final Pattern VPA = Pattern.compile("[A-Za-z0-9._-]{1,64}@[A-Za-z0-9.-]{2,64}");
    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private final BillingPaymentRepository payments;
    private final BillingPaymentProperties properties;
    private final ObjectProvider<LocalPaymentDemoGateway> gateways;
    private final BillingPaymentTransaction paymentTransaction;
    private final ProviderPaymentConfirmationService confirmations;

    public LocalPaymentResponse create(UUID workspaceId, LocalPaymentRequest request) {
        BillingPayment payment = pendingPayment(workspaceId, request.orderId());
        LocalPaymentDemoGateway gateway = gateway();
        if (request.method() == LocalPaymentMethod.CARD && !gateway.cardEnabled()) {
            throw new BusinessRuleViolationException("Card checkout is available only for the MY local Razorpay demo");
        }
        if (payment.hasProviderPaymentIdentity()) {
            var existing = gateway.get(payment.getProviderOrderId(), payment.getProviderPaymentId());
            LocalPaymentResponse response = response(payment, existing);
            applyTrustedStatus(gateway, existing);
            return response;
        }
        var initiation = paymentTransaction.initiateLocalPayment(payment.getId(), gateway, request.method(),
                details(request), "as-local-payment-" + payment.getId());
        var result = initiation.created() ? initiation.result()
                : gateway.get(payment.getProviderOrderId(), initiation.paymentId());
        LocalPaymentResponse response = response(payment, result);
        applyTrustedStatus(gateway, result);
        return response;
    }

    public LocalPaymentResponse get(UUID workspaceId, String orderId, String paymentId) {
        BillingPayment payment = pendingPayment(workspaceId, orderId);
        LocalPaymentDemoGateway gateway = gateway();
        var result = gateway.get(payment.getProviderOrderId(), paymentId);
        LocalPaymentResponse response = response(payment, result);
        applyTrustedStatus(gateway, result);
        return response;
    }

    private void applyTrustedStatus(LocalPaymentDemoGateway gateway,
                                    LocalPaymentDemoGateway.LocalPaymentResult result) {
        if (("CAPTURED".equals(result.providerPaymentStatus()) || "SETTLED".equals(result.providerPaymentStatus()))
                && gateway.pollConfirmationEnabled()) {
            confirmations.succeeded(PaymentProvider.RAZORPAY_LOCAL, result.orderId(), result.paymentId(),
                    result.amountMinor(), result.currency());
        } else if ("CANCELLED".equals(result.providerPaymentStatus())) {
            confirmations.canceled(PaymentProvider.RAZORPAY_LOCAL, result.orderId(), result.paymentId(),
                    result.amountMinor(), result.currency());
        } else if ("FAILED".equals(result.providerPaymentStatus())
                || "AUTH_EXPIRED".equals(result.providerPaymentStatus())) {
            confirmations.failed(PaymentProvider.RAZORPAY_LOCAL, result.orderId(), result.paymentId(),
                    result.amountMinor(), result.currency(), result.providerPaymentStatus());
        }
    }

    private BillingPayment pendingPayment(UUID workspaceId, String orderId) {
        BillingPayment payment = payments.findByProviderAndProviderOrderId(PaymentProvider.RAZORPAY_LOCAL, orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Local payment order was not found"));
        if (!payment.getWorkspaceId().equals(workspaceId)) {
            throw new AuthorizationDeniedException("Payment order does not belong to this workspace");
        }
        if (payment.getProvider() != PaymentProvider.RAZORPAY_LOCAL) {
            throw new BusinessRuleViolationException("Payment order is not a local Razorpay order");
        }
        if (payment.getStatus() == PaymentStatus.FAILED || payment.getStatus() == PaymentStatus.CANCELED) {
            throw new BusinessRuleViolationException("This payment attempt is complete; start a new checkout to retry");
        }
        if (payment.getStatus() != PaymentStatus.ORDER_CREATED || payment.getProviderOrderId() == null) {
            throw new BusinessRuleViolationException("A pending local Razorpay order is required");
        }
        if (payment.getRequestedPlan() != Plan.PRO || payment.getAmountMinor() != properties.getProPriceMinor()
                || !payment.getCurrency().equalsIgnoreCase(properties.getCurrency())) {
            throw new BusinessRuleViolationException("Payment order does not match the configured PRO purchase");
        }
        return payment;
    }

    private Map<String, String> details(LocalPaymentRequest request) {
        return switch (request.method()) {
            case CARD -> cardDetails(request);
            case UPI -> Map.of("VPA", valid(request.vpa(), VPA, "Enter a valid demo VPA"));
            case NETBANKING -> Map.of("BANK", valid(request.bank(), CODE, "Enter a valid demo bank code"));
            case WALLET -> Map.of("WALLET_CODE", valid(request.walletCode(), CODE, "Enter a valid demo wallet code"));
        };
    }

    private Map<String, String> cardDetails(LocalPaymentRequest request) {
        String pan = request.pan() == null ? "" : request.pan().trim();
        if (!pan.matches("[0-9]{13,19}") || !luhn(pan)) {
            throw new InvalidRequestException("Enter a valid card number");
        }
        String cvv = request.cvv() == null ? "" : request.cvv().trim();
        if (!cvv.matches("[0-9]{3,4}")) throw new InvalidRequestException("Enter a valid CVV");
        if (request.expiryMonth() == null || request.expiryMonth() < 1 || request.expiryMonth() > 12
                || request.expiryYear() == null || request.expiryYear() < Year.now().getValue()) {
            throw new InvalidRequestException("Enter a valid card expiry");
        }
        String holder = request.cardHolderName() == null ? "" : request.cardHolderName().trim();
        if (holder.length() < 3) throw new InvalidRequestException("Enter the cardholder name");
        return Map.of("PAN", pan, "CVV", cvv, "EXPIRY_MONTH", request.expiryMonth().toString(),
                "EXPIRY_YEAR", request.expiryYear().toString(), "CARD_HOLDER_NAME", holder);
    }

    private boolean luhn(String pan) {
        int sum = 0;
        boolean doubleDigit = false;
        for (int index = pan.length() - 1; index >= 0; index--) {
            int digit = pan.charAt(index) - '0';
            if (doubleDigit && (digit *= 2) > 9) digit -= 9;
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }

    private String valid(String value, Pattern pattern, String message) {
        if (value == null || !pattern.matcher(value.trim()).matches()) throw new InvalidRequestException(message);
        return value.trim();
    }

    private LocalPaymentDemoGateway gateway() {
        return gateways.orderedStream().findFirst()
                .orElseThrow(() -> new ServiceUnavailableException("Local payment checkout is unavailable", null));
    }

    private LocalPaymentResponse response(BillingPayment payment, LocalPaymentDemoGateway.LocalPaymentResult result) {
        if (!payment.getProviderOrderId().equals(result.orderId())
                || payment.getAmountMinor() != result.amountMinor()
                || !payment.getCurrency().equalsIgnoreCase(result.currency())) {
            throw new ServiceUnavailableException("Local payment service returned inconsistent payment details", null);
        }
        return new LocalPaymentResponse(result.paymentId(), result.orderId(), result.providerPaymentStatus(),
                result.method(), result.amountMinor(), result.currency(), result.createdAt());
    }
}
