package com.assetsphere.modules.billing.api.dto.response;

import com.assetsphere.modules.billing.api.PaymentProvider;

public record PaymentCapabilitiesResponse(PaymentProvider provider, boolean supportsHostedCheckout,
                                          boolean supportsOrderCreation,
                                          boolean localPollConfirmationEnabled,
                                          boolean localCardEnabled) {
}
