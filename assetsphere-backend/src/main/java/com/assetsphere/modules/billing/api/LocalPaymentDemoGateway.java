package com.assetsphere.modules.billing.api;

import java.time.Instant;
import java.util.Map;

public interface LocalPaymentDemoGateway {
    LocalPaymentResult create(String orderId, LocalPaymentMethod method, Map<String, String> methodDetails,
                              String idempotencyKey);

    LocalPaymentResult get(String orderId, String paymentId);

    default boolean pollConfirmationEnabled() { return false; }

    default boolean cardEnabled() { return false; }

    record LocalPaymentResult(String paymentId, String orderId, String providerPaymentStatus,
                              LocalPaymentMethod method, long amountMinor, String currency, Instant createdAt) { }
}
