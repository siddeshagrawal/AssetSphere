package com.assetsphere.modules.billing.api;

public record CheckoutSession(String providerOrderId, String clientKeyId, String checkoutUrl,
                              long amountMinor, String currency, String providerOrderStatus) { }
