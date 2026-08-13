package com.assetsphere.modules.billing.api;

import java.time.Instant;

public record PaymentWebhookEvent(PaymentProvider provider, String eventId, String eventType,
                                  String providerOrderId, String providerPaymentId, long amountMinor,
                                  String currency, PaymentWebhookStatus status, Instant occurredAt,
                                  boolean verified, Instant periodStart, Instant periodEnd,
                                  Boolean cancelAtPeriodEnd) {
    public PaymentWebhookEvent(PaymentProvider provider, String eventId, String eventType,
                               String providerOrderId, String providerPaymentId, long amountMinor,
                               String currency, PaymentWebhookStatus status, Instant occurredAt,
                               boolean verified) {
        this(provider, eventId, eventType, providerOrderId, providerPaymentId, amountMinor, currency,
                status, occurredAt, verified, null, null, null);
    }
}
