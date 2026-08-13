package com.assetsphere.infrastructure.config;

import com.assetsphere.modules.auth.api.GoogleOAuthConfigurationGuard;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionConfigurationValidator {
    private final Environment environment;
    private final ApplicationProperties applicationProperties;
    private final GoogleOAuthConfigurationGuard googleOAuthConfigurationGuard;

    public ProductionConfigurationValidator(Environment environment, ApplicationProperties applicationProperties,
                                            GoogleOAuthConfigurationGuard googleOAuthConfigurationGuard) {
        this.environment = environment;
        this.applicationProperties = applicationProperties;
        this.googleOAuthConfigurationGuard = googleOAuthConfigurationGuard;
    }

    @PostConstruct
    void validate() {
        require("spring.datasource.url", "Database URL");
        require("spring.datasource.username", "Database username");
        require("spring.datasource.password", "Database password");
        require("spring.kafka.bootstrap-servers", "Kafka bootstrap servers");
        require("assetsphere.cors.allowed-origins", "CORS allowed origins");
        require("assetsphere.jwt.secret", "JWT secret");

        String paymentMode = require("assetsphere.billing.payment-mode", "Payment mode");
        if ("RAZORPAY_LOCAL".equalsIgnoreCase(paymentMode)) {
            throw new IllegalStateException("Local Razorpay is not permitted in production");
        }
        if (!"STRIPE".equalsIgnoreCase(paymentMode)) {
            throw new IllegalStateException("Production payment mode must be STRIPE");
        }
        if (!environment.getProperty("assetsphere.billing.stripe.enabled", Boolean.class, false)) {
            throw new IllegalStateException("Selected payment provider STRIPE is disabled");
        }
        require("assetsphere.billing.stripe.secret-key", "Stripe secret key");
        require("assetsphere.billing.stripe.publishable-key", "Stripe publishable key");
        require("assetsphere.billing.stripe.pro-price-id", "Stripe PRO price id");
        require("assetsphere.billing.stripe.webhook-secret", "Stripe webhook secret");
        require("assetsphere.notification.frontend-base-url", "Frontend base URL");

        var minio = applicationProperties.getStorage().getMinio();
        if (minio.isEnabled()) {
            requireValue(minio.getEndpoint(), "Object storage endpoint");
            requireValue(minio.getAccessKey(), "Object storage access key");
            requireValue(minio.getSecretKey(), "Object storage secret key");
            requireValue(minio.getBucket(), "Object storage bucket");
        }

        googleOAuthConfigurationGuard.validateIfEnabled();

        if (environment.getProperty("assetsphere.notification.email.enabled", Boolean.class, false)) {
            require("assetsphere.notification.email.from", "Invitation email sender address");
            String mailHost = require("spring.mail.host", "SMTP host");
            if ("localhost".equalsIgnoreCase(mailHost) || "127.0.0.1".equals(mailHost)) {
                throw new IllegalStateException("SMTP host must not use a local development address in production");
            }
        }

        if (environment.getProperty("assetsphere.ai.enabled", Boolean.class, false)) {
            require("spring.ai.openai.api-key", "OpenAI API key");
        }
    }

    private String require(String property, String label) {
        String value = environment.getProperty(property);
        requireValue(value, label);
        return value;
    }

    private void requireValue(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalStateException(label + " is not configured");
    }
}
