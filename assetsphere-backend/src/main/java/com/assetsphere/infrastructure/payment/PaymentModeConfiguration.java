package com.assetsphere.infrastructure.payment;

import com.assetsphere.modules.billing.api.PaymentMode;
import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "assetsphere.billing")
class PaymentModeConfiguration {
    private final Environment environment;
    private PaymentMode paymentMode = PaymentMode.STRIPE;

    PaymentModeConfiguration(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        var activeProfiles = Arrays.asList(environment.getActiveProfiles());
        validate(activeProfiles);
        if (paymentMode == PaymentMode.RAZORPAY_LOCAL) {
            LocalRazorpayVariant.parse(environment.getProperty("assetsphere.billing.local-razorpay.variant"));
        }
        String enabledProperty = paymentMode == PaymentMode.STRIPE
                ? "assetsphere.billing.stripe.enabled"
                : "assetsphere.billing.local-razorpay.enabled";
        if (!environment.getProperty(enabledProperty, Boolean.class, false)) {
            throw new IllegalStateException("Selected payment provider " + paymentMode + " is disabled");
        }
    }

    void validate(Iterable<String> activeProfiles) {
        for (String profile : activeProfiles) {
            if ("prod".equalsIgnoreCase(profile) && paymentMode == PaymentMode.RAZORPAY_LOCAL) {
                throw new IllegalStateException("Local Razorpay is not permitted in production");
            }
        }
    }
}
