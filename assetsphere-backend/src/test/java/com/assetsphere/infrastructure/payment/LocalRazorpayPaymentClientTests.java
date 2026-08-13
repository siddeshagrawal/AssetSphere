package com.assetsphere.infrastructure.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.assetsphere.modules.billing.api.LocalPaymentMethod;
import com.assetsphere.modules.common.exception.ServiceUnavailableException;
import java.util.Base64;
import java.util.Map;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class LocalRazorpayPaymentClientTests {
    @Test
    void tokenizesCardThenInitiatesPaymentWithTokenOnly() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:8082/v1/vault/tokenize"))
                .andExpect(header("X-Idempotency-Key", "payment-key-vault"))
                .andExpect(content().json("""
                        {"pan":"4111111111111111","cvv":"123","expiryMonth":12,"expiryYear":2027,"customerId":null,"cardHolderName":"Demo User"}
                        """))
                .andRespond(withSuccess("""
                        {"token":"tok_demo","lastFour":"1111","brand":"VISA","expiryMonth":12,"expiryYear":2027}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost:8082/v1/payments"))
                .andExpect(content().json("""
                        {"orderId":"order_1","method":"CARD","methodDetails":{"token":"tok_demo"}}
                        """))
                .andRespond(withSuccess("""
                        {"id":"payment_1","orderId":"order_1","amount":{"amountUnits":99900,"currency":"INR"},"status":"AUTHORIZING","method":"CARD","createdAt":"2026-08-11T00:00:00"}
                        """, MediaType.APPLICATION_JSON));

        var result = new LocalRazorpayPaymentClient(properties(), builder).create("order_1", LocalPaymentMethod.CARD,
                Map.of("PAN", "4111111111111111", "CVV", "123", "EXPIRY_MONTH", "12",
                        "EXPIRY_YEAR", "2027", "CARD_HOLDER_NAME", "Demo User"), "payment-key");

        assertThat(result.method()).isEqualTo(LocalPaymentMethod.CARD);
        server.verify();
    }
    @Test
    void createsAndNormalizesUpiPayment() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LocalRazorpayProperties properties = properties();
        server.expect(requestTo("http://localhost:8082/v1/payments"))
                .andExpect(header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                        "key:secret".getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                .andExpect(header("X-Idempotency-Key", "payment-key"))
                .andExpect(content().json("""
                        {"orderId":"order_1","method":"UPI","methodDetails":{"VPA":"demo@bank"}}
                        """))
                .andRespond(withSuccess("""
                        {"id":"payment_1","orderId":"order_1","amount":{"amountUnits":99900,"currency":"INR"},"status":"AUTHORIZING","method":"UPI","createdAt":"2026-08-11T00:00:00"}
                        """, MediaType.APPLICATION_JSON));

        var result = new LocalRazorpayPaymentClient(properties, builder)
                .create("order_1", LocalPaymentMethod.UPI, Map.of("VPA", "demo@bank"), "payment-key");

        assertThat(result.paymentId()).isEqualTo("payment_1");
        assertThat(result.providerPaymentStatus()).isEqualTo("AUTHORIZING");
        server.verify();
    }

    @Test
    void mapsProviderRateLimitToSafeTypedFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:8082/v1/payments"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> new LocalRazorpayPaymentClient(properties(), builder)
                .create("order_1", LocalPaymentMethod.WALLET, Map.of("WALLET_CODE", "DEMO"), "payment-key"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("Payment service is temporarily busy. Please retry shortly.");
    }

    @Test
    void retrievesAndMapsProviderPaymentStatus() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:8082/v1/orders/order_1/payments"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{"id":"payment_1","orderId":"order_1","amount":{"amountUnits":99900,"currency":"INR"},"status":"CAPTURED","method":"UPI","createdAt":"2026-08-11T00:00:00"}]
                        """, MediaType.APPLICATION_JSON));

        var result = new LocalRazorpayPaymentClient(properties(), builder).get("order_1", "payment_1");

        assertThat(result.providerPaymentStatus()).isEqualTo("CAPTURED");
        server.verify();
    }

    @Test
    void shortCacheDoesNotHideProviderTransitions() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MutableClock clock = new MutableClock(Instant.parse("2026-08-11T00:00:00Z"));
        server.expect(requestTo("http://localhost:8082/v1/payments")).andRespond(withSuccess("""
                {"id":"payment_1","orderId":"order_1","amount":{"amountUnits":99900,"currency":"INR"},"status":"AUTHORIZING","method":"UPI","createdAt":"2026-08-11T00:00:00"}
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost:8082/v1/orders/order_1/payments")).andRespond(withSuccess("""
                [{"id":"payment_1","orderId":"order_1","amount":{"amountUnits":99900,"currency":"INR"},"status":"CAPTURED","method":"UPI","createdAt":"2026-08-11T00:00:00"}]
                """, MediaType.APPLICATION_JSON));
        var client = new LocalRazorpayPaymentClient(properties(), builder, clock);
        client.create("order_1", LocalPaymentMethod.UPI, Map.of("VPA", "demo@bank"), "payment-key");

        clock.advanceSeconds(3);

        assertThat(client.get("order_1", "payment_1").providerPaymentStatus()).isEqualTo("CAPTURED");
        server.verify();
    }

    @Test
    void terminalStatusIsServedFromLongerCache() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:8082/v1/orders/order_1/payments")).andRespond(withSuccess("""
                [{"id":"payment_1","orderId":"order_1","amount":{"amountUnits":99900,"currency":"INR"},"status":"CAPTURED","method":"UPI","createdAt":"2026-08-11T00:00:00"}]
                """, MediaType.APPLICATION_JSON));
        var client = new LocalRazorpayPaymentClient(properties(), builder);

        assertThat(client.get("order_1", "payment_1").providerPaymentStatus()).isEqualTo("CAPTURED");
        assertThat(client.get("order_1", "payment_1").providerPaymentStatus()).isEqualTo("CAPTURED");
        server.verify();
    }

    @Test
    void retryAfterServesNonTerminalStaleStateWithoutImmediateProviderRetry() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MutableClock clock = new MutableClock(Instant.parse("2026-08-11T00:00:00Z"));
        server.expect(requestTo("http://localhost:8082/v1/payments")).andRespond(withSuccess("""
                {"id":"payment_1","orderId":"order_1","amount":{"amountUnits":99900,"currency":"INR"},"status":"AUTHORIZING","method":"UPI","createdAt":"2026-08-11T00:00:00"}
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost:8082/v1/orders/order_1/payments"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "30"));
        var client = new LocalRazorpayPaymentClient(properties(), builder, clock);
        client.create("order_1", LocalPaymentMethod.UPI, Map.of("VPA", "demo@bank"), "payment-key");
        clock.advanceSeconds(3);

        assertThat(client.get("order_1", "payment_1").providerPaymentStatus()).isEqualTo("AUTHORIZING");
        clock.advanceSeconds(5);
        assertThat(client.get("order_1", "payment_1").providerPaymentStatus()).isEqualTo("AUTHORIZING");
        server.verify();
    }

    @Test
    void pollConfirmationIsMyOnlyAndProductionGuardRejectsIt() {
        LocalRazorpayProperties properties = properties();
        properties.setPollConfirmationEnabled(true);
        assertThat(new LocalRazorpayPaymentClient(properties, RestClient.builder()).pollConfirmationEnabled()).isTrue();
        assertThat(new LocalRazorpayPaymentClient(properties, RestClient.builder()).cardEnabled()).isTrue();

        properties.setVariant("TUTOR");
        assertThat(new LocalRazorpayPaymentClient(properties, RestClient.builder()).pollConfirmationEnabled()).isFalse();
        assertThat(new LocalRazorpayPaymentClient(properties, RestClient.builder()).cardEnabled()).isFalse();

        assertThatThrownBy(new LocalRazorpayProductionGuard(properties)::rejectLocalDemoFeatures)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("forbidden in production");
    }

    private LocalRazorpayProperties properties() {
        LocalRazorpayProperties properties = new LocalRazorpayProperties();
        properties.setEnabled(true);
        properties.setVariant("MY");
        properties.setBaseUrl("http://localhost:8082");
        properties.setKeyId("key");
        properties.setKeySecret("secret");
        return properties;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        void advanceSeconds(long seconds) { instant = instant.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
