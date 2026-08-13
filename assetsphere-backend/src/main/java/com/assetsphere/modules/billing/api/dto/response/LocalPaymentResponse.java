package com.assetsphere.modules.billing.api.dto.response;

import com.assetsphere.modules.billing.api.LocalPaymentMethod;
import java.time.Instant;

public record LocalPaymentResponse(String paymentId, String orderId, String providerPaymentStatus,
                                   LocalPaymentMethod method, long amountMinor, String currency, Instant createdAt) { }
