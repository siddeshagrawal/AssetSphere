package com.assetsphere.infrastructure.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.assetsphere.modules.billing.api.PaymentGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

class PaymentGatewaySelectionTests {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(GatewayConfiguration.class);

    @Test
    void selectsOnlyStripeGateway() {
        contextRunner.withPropertyValues("assetsphere.billing.payment-mode=STRIPE")
                .run(context -> {
                    assertThat(context).hasSingleBean(PaymentGateway.class);
                    assertThat(context.getBean(PaymentGateway.class)).isInstanceOf(StripePaymentGateway.class);
                });
    }

    @Test
    void selectsOnlyLocalRazorpayGateway() {
        contextRunner.withPropertyValues("assetsphere.billing.payment-mode=RAZORPAY_LOCAL")
                .run(context -> {
                    assertThat(context).hasSingleBean(PaymentGateway.class);
                    assertThat(context.getBean(PaymentGateway.class)).isInstanceOf(LocalRazorpayPaymentGateway.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({StripePaymentGateway.class, LocalRazorpayPaymentGateway.class,
            StripeProperties.class, LocalRazorpayProperties.class})
    static class GatewayConfiguration {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean RestClient.Builder restClientBuilder() { return RestClient.builder(); }
    }
}
