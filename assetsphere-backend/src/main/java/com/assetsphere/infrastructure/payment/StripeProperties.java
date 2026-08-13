package com.assetsphere.infrastructure.payment;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "assetsphere.billing.stripe")
class StripeProperties {
    private boolean enabled;
    private String secretKey;
    private String publishableKey;
    private String webhookSecret;
    private String proPriceId;
    private String baseUrl = "https://api.stripe.com";

    void requireCheckout() {
        require(secretKey, "Stripe secret key");
        require(publishableKey, "Stripe publishable key");
        require(proPriceId, "Stripe PRO price id");
    }

    void requireWebhook() { require(webhookSecret, "Stripe webhook secret"); }

    boolean checkoutConfigured() {
        return enabled && present(secretKey) && present(publishableKey) && present(proPriceId);
    }

    boolean webhookConfigured() { return present(webhookSecret); }

    private void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is not configured");
    }

    private boolean present(String value) { return value != null && !value.isBlank(); }
}
