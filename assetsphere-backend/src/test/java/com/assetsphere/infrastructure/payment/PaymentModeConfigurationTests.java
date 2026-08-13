package com.assetsphere.infrastructure.payment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetsphere.modules.billing.api.PaymentMode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class PaymentModeConfigurationTests {
    @Test
    void allowsStripeAndLocalRazorpayInDevelopment() {
        PaymentModeConfiguration configuration = new PaymentModeConfiguration(new MockEnvironment());
        configuration.setPaymentMode(PaymentMode.STRIPE);
        assertThatCode(() -> configuration.validate(List.of("dev"))).doesNotThrowAnyException();
        configuration.setPaymentMode(PaymentMode.RAZORPAY_LOCAL);
        assertThatCode(() -> configuration.validate(List.of("dev"))).doesNotThrowAnyException();
    }

    @Test
    void allowsOnlyStripeInProduction() {
        PaymentModeConfiguration configuration = new PaymentModeConfiguration(new MockEnvironment());
        configuration.setPaymentMode(PaymentMode.STRIPE);
        assertThatCode(() -> configuration.validate(List.of("prod"))).doesNotThrowAnyException();
        configuration.setPaymentMode(PaymentMode.RAZORPAY_LOCAL);
        assertThatThrownBy(() -> configuration.validate(List.of("prod")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Local Razorpay is not permitted in production");
    }

    @Test
    void selectedDisabledProviderFailsWithoutFallback() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("assetsphere.billing.local-razorpay.enabled", "false")
                .withProperty("assetsphere.billing.local-razorpay.variant", "MY");
        environment.setActiveProfiles("dev");
        PaymentModeConfiguration configuration = new PaymentModeConfiguration(environment);
        configuration.setPaymentMode(PaymentMode.RAZORPAY_LOCAL);

        assertThatThrownBy(configuration::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Selected payment provider RAZORPAY_LOCAL is disabled");
    }

    @Test
    void rejectsUnknownLocalRazorpayVariant() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("assetsphere.billing.local-razorpay.enabled", "true")
                .withProperty("assetsphere.billing.local-razorpay.variant", "unknown");
        PaymentModeConfiguration configuration = new PaymentModeConfiguration(environment);
        configuration.setPaymentMode(PaymentMode.RAZORPAY_LOCAL);

        assertThatThrownBy(configuration::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unsupported local Razorpay contract variant: unknown");
    }

    @Test
    void stripeIgnoresLocalRazorpayVariant() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("assetsphere.billing.stripe.enabled", "true")
                .withProperty("assetsphere.billing.local-razorpay.variant", "unknown");
        PaymentModeConfiguration configuration = new PaymentModeConfiguration(environment);
        configuration.setPaymentMode(PaymentMode.STRIPE);

        assertThatCode(configuration::validate).doesNotThrowAnyException();
    }
}
