package com.assetsphere.infrastructure.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.assetsphere.modules.billing.api.CheckoutRequest;
import com.assetsphere.modules.billing.api.PaymentWebhookStatus;
import com.assetsphere.modules.billing.api.Plan;
import com.assetsphere.modules.common.exception.InvalidRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class LocalRazorpayPaymentGatewayTests {
    private static final String WEBHOOK_SECRET = "test-webhook-secret";

    @Test
    void mapsTutorOrderResponseUsingBasicAuthAndNestedMoney() {
        LocalRazorpayProperties properties = properties();
        properties.setVariant("TUTOR");
        properties.setKeyId("local_key");
        properties.setKeySecret("local_secret");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:8082/v1/orders"))
                .andExpect(header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                        "local_key:local_secret".getBytes(StandardCharsets.UTF_8))))
                .andExpect(header("X-Idempotency-Key", "receipt_1"))
                .andExpect(content().json("""
                        {"amount":{"amountUnits":99900,"currency":"INR"},"receipt":"receipt_1"}
                        """, false))
                .andRespond(withSuccess("""
                        {"id":"2be3c83a-65c8-41a4-a836-8f5a477b8762","amount":{"amountUnits":99900,"currency":"INR"},"status":"CREATED"}
                        """, MediaType.APPLICATION_JSON));
        LocalRazorpayPaymentGateway gateway = new LocalRazorpayPaymentGateway(properties, new ObjectMapper(), builder);

        var session = gateway.createCheckout(new CheckoutRequest(
                UUID.randomUUID(), UUID.randomUUID(), Plan.PRO, 99_900, "INR", "receipt_1"));

        assertThat(session.providerOrderId()).isEqualTo("2be3c83a-65c8-41a4-a836-8f5a477b8762");
        assertThat(session.providerOrderStatus()).isEqualTo("CREATED");
        assertThat(session.checkoutUrl()).isNull();
        assertThat(gateway.supportsHostedCheckout()).isFalse();
        server.verify();
    }

    @Test
    void mapsMyOrderResponseToTheSameBillingResult() {
        LocalRazorpayProperties properties = properties();
        properties.setVariant("MY");
        properties.setKeyId("local_key");
        properties.setKeySecret("local_secret");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:8082/v1/orders"))
                .andExpect(content().json("""
                        {"amount":{"amountUnits":99900,"currency":"INR"},"receipt":"receipt_1"}
                        """, false))
                .andRespond(withSuccess("""
                        {"orderId":"2be3c83a-65c8-41a4-a836-8f5a477b8762","amount":{"amountUnits":99900,"currency":"INR"},"orderStatus":"CREATED"}
                        """, MediaType.APPLICATION_JSON));
        LocalRazorpayPaymentGateway gateway = new LocalRazorpayPaymentGateway(properties, new ObjectMapper(), builder);

        var session = gateway.createCheckout(new CheckoutRequest(
                UUID.randomUUID(), UUID.randomUUID(), Plan.PRO, 99_900, "INR", "receipt_1"));

        assertThat(session.providerOrderId()).isEqualTo("2be3c83a-65c8-41a4-a836-8f5a477b8762");
        assertThat(session.providerOrderStatus()).isEqualTo("CREATED");
        assertThat(session.amountMinor()).isEqualTo(99_900);
        assertThat(session.currency()).isEqualTo("INR");
        server.verify();
    }

    @Test
    void verifiesAndMapsLocalWebhookStatesWithStableDeduplication() throws Exception {
        LocalRazorpayPaymentGateway gateway = gateway();
        String success = """
                {"event":"PAYMENT_STATUS_CHANGED","payload":{"orderId":"order_1","paymentId":"payment_1","paymentStatus":"CAPTURED","amountUnits":99900,"amountCurrency":"INR"}}
                """;
        String created = """
                {"event":"PAYMENT_CREATED","payload":{"orderId":"order_1","paymentId":"payment_1","paymentStatus":"AUTHORIZING","amountUnits":99900,"amountCurrency":"INR"}}
                """;
        String failed = """
                {"event":"PAYMENT_STATUS_CHANGED","payload":{"orderId":"order_1","paymentId":"payment_1","paymentStatus":"FAILED","amountUnits":99900,"amountCurrency":"INR"}}
                """;
        String orderCreated = """
                {"event":"ORDER_CREATED","payload":{"orderId":"order_1","amountUnits":99900,"amountCurrency":"INR"}}
                """;
        String orderCanceled = """
                {"event":"ORDER_CANCELLED","payload":{"orderId":"order_1","amountUnits":99900,"amountCurrency":"INR"}}
                """;

        var successEvent = gateway.verifyWebhook(null, success, signature(success));

        assertThat(successEvent.status()).isEqualTo(PaymentWebhookStatus.SUCCEEDED);
        assertThat(gateway.verifyWebhook(null, success, signature(success)).eventId()).isEqualTo(successEvent.eventId());
        assertThat(gateway.verifyWebhook(null, created, signature(created)).status()).isEqualTo(PaymentWebhookStatus.IGNORED);
        assertThat(gateway.verifyWebhook(null, failed, signature(failed)).status()).isEqualTo(PaymentWebhookStatus.FAILED);
        assertThat(gateway.verifyWebhook(null, orderCreated, signature(orderCreated)).status())
                .isEqualTo(PaymentWebhookStatus.IGNORED);
        assertThat(gateway.verifyWebhook(null, orderCanceled, signature(orderCanceled)).status())
                .isEqualTo(PaymentWebhookStatus.CANCELED);
    }

    @Test
    void rejectsInvalidWebhookSignature() {
        assertThatThrownBy(() -> gateway().verifyWebhook(null, "{}", "0".repeat(64)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Invalid local Razorpay webhook signature");
    }

    private LocalRazorpayPaymentGateway gateway() {
        return new LocalRazorpayPaymentGateway(properties(), new ObjectMapper(), RestClient.builder());
    }

    private LocalRazorpayProperties properties() {
        LocalRazorpayProperties properties = new LocalRazorpayProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://localhost:8082");
        properties.setWebhookSecret(WEBHOOK_SECRET);
        return properties;
    }

    private String signature(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
