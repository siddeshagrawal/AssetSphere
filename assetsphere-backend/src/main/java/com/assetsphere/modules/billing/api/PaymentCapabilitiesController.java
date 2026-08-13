package com.assetsphere.modules.billing.api;

import com.assetsphere.modules.billing.api.dto.response.PaymentCapabilitiesResponse;
import com.assetsphere.modules.common.exception.ServiceUnavailableException;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/billing/payment-capabilities")
@SecurityRequirement(name = "bearerAuth")
class PaymentCapabilitiesController {
    private final ObjectProvider<PaymentGateway> gateways;
    private final ObjectProvider<LocalPaymentDemoGateway> localGateways;
    private final ClockProvider clock;

    @GetMapping
    ApiResponse<PaymentCapabilitiesResponse> capabilities() {
        PaymentGateway gateway = gateways.orderedStream().findFirst()
                .orElseThrow(() -> new ServiceUnavailableException("The selected payment provider is not configured", null));
        LocalPaymentDemoGateway localGateway = gateway.provider() == PaymentProvider.RAZORPAY_LOCAL
                ? localGateways.orderedStream().findFirst().orElse(null) : null;
        return ApiResponse.success(new PaymentCapabilitiesResponse(
                gateway.provider(), gateway.supportsHostedCheckout(), gateway.available(),
                localGateway != null && localGateway.pollConfirmationEnabled(),
                localGateway != null && localGateway.cardEnabled()), clock);
    }
}
