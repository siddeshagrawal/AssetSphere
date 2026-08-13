package com.assetsphere.modules.billing.api;

public interface PaymentGateway {
    PaymentProvider provider();
    boolean supportsHostedCheckout();
    String clientKeyId();
    default boolean available() { return true; }
    CheckoutSession createCheckout(CheckoutRequest request);
    PaymentWebhookEvent verifyWebhook(String providerEventId, String payload, String signature);
    default boolean supportsCancellation() { return false; }
    default void cancelAtPeriodEnd(String externalSubscriptionId) {
        throw new UnsupportedOperationException("Subscription cancellation is not supported");
    }
}
