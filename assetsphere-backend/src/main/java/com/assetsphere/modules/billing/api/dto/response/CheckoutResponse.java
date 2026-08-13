package com.assetsphere.modules.billing.api.dto.response;

import com.assetsphere.modules.billing.api.PaymentProvider;
import com.assetsphere.modules.billing.api.PaymentStatus;

public record CheckoutResponse(PaymentProvider provider, String keyId, String orderId, String checkoutUrl,
                               String paymentId, boolean supportsHostedCheckout, String providerOrderStatus,
                               long amountMinor, String currency, PaymentStatus paymentStatus) { }
