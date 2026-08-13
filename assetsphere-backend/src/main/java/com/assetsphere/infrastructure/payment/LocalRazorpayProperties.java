package com.assetsphere.infrastructure.payment;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "assetsphere.billing.local-razorpay")
class LocalRazorpayProperties {
    private boolean enabled;
    private String keyId;
    private String keySecret;
    private String webhookSecret;
    private String baseUrl = "http://localhost:8082";
    private String variant;
    private boolean pollConfirmationEnabled;
    private Duration nonTerminalStatusCacheTtl = Duration.ofSeconds(3);
    private Duration terminalStatusCacheTtl = Duration.ofSeconds(30);

    void requireOrderConfiguration() {
        requireVariant();
        require(keyId, "Local Razorpay key id");
        require(keySecret, "Local Razorpay key secret");
        require(baseUrl, "Local Razorpay base URL");
    }

    LocalRazorpayVariant requireVariant() {
        return LocalRazorpayVariant.parse(variant);
    }

    void requireWebhookConfiguration() {
        require(webhookSecret, "Local Razorpay webhook secret");
    }

    boolean orderConfigured() {
        return enabled && keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank()
                && baseUrl != null && !baseUrl.isBlank() && variant != null && !variant.isBlank();
    }

    private void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is not configured");
    }
}
