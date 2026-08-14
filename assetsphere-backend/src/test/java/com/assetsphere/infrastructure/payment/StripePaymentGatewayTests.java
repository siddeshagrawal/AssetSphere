package com.assetsphere.infrastructure.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetsphere.modules.billing.api.PaymentWebhookStatus;
import com.assetsphere.modules.billing.api.CheckoutRequest;
import com.assetsphere.modules.billing.api.Plan;
import com.assetsphere.modules.billing.api.ProviderSubscriptionStatus;
import com.assetsphere.modules.common.exception.InvalidRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.http.MediaType;

class StripePaymentGatewayTests {
    @Test
    void schedulesRecurringSubscriptionCancellationAtPeriodEnd() {
        StripeProperties properties = new StripeProperties();
        properties.setSecretKey("secret");
        properties.setPublishableKey("key");
        properties.setProPriceId("price_1");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.stripe.com/v1/subscriptions/sub_1"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("cancel_at_period_end=true")))
                .andRespond(withSuccess("{\"id\":\"sub_1\",\"cancel_at_period_end\":true}", MediaType.APPLICATION_JSON));

        new StripePaymentGateway(properties, new ObjectMapper(), builder, "https://app.example.com")
                .cancelAtPeriodEnd("sub_1");

        server.verify();
    }
    @Test
    void verifiesSignedCheckoutCompletionAndRejectsTampering() throws Exception {
        StripeProperties properties = new StripeProperties();
        properties.setWebhookSecret("whsec_test");
        StripePaymentGateway gateway = new StripePaymentGateway(properties, new ObjectMapper(), RestClient.builder(),
                "https://app.example.com");
        long timestamp = Instant.now().getEpochSecond();
        String payload = "{\"id\":\"evt_1\",\"type\":\"checkout.session.completed\",\"created\":" + timestamp
                + ",\"data\":{\"object\":{\"id\":\"cs_1\",\"payment_status\":\"paid\",\"subscription\":\"sub_1\",\"amount_total\":99900,\"currency\":\"inr\"}}}";
        String header = "t=" + timestamp + ",v1=" + hmac("whsec_test", timestamp + "." + payload);

        var event = gateway.verifyWebhook(null, payload, header);

        assertThat(event.eventId()).isEqualTo("evt_1");
        assertThat(event.status()).isEqualTo(PaymentWebhookStatus.SUCCEEDED);
        assertThatThrownBy(() -> gateway.verifyWebhook(null, payload + " ", header)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void checkoutSubscriptionIdentityPrefersSubscriptionOverPaymentIntent() throws Exception {
        var event = verifiedEvent("checkout.session.completed", """
                {"id":"cs_1","mode":"subscription","payment_status":"paid","subscription":"sub_123",
                 "payment_intent":"pi_ignored","amount_total":99900,"currency":"inr"}
                """);

        assertThat(event.providerPaymentId()).isEqualTo("sub_123").doesNotStartWith("pi_");
    }

    @Test
    void invoiceResolvesCurrentNestedSubscriptionIdentity() throws Exception {
        var event = verifiedEvent("invoice.paid", """
                {"id":"in_1","amount_paid":99900,"currency":"inr","period_start":1785522600,
                 "period_end":1788201000,"subscription":"sub_legacy",
                 "parent":{"type":"subscription_details","subscription_details":{"subscription":"sub_123"}}}
                """);

        assertThat(event.providerPaymentId()).isEqualTo("sub_123");
        assertThat(event.periodStart()).isNull();
        assertThat(event.periodEnd()).isNull();
    }

    @Test
    void invoiceKeepsLegacyTopLevelSubscriptionCompatibility() throws Exception {
        var event = verifiedEvent("invoice.paid", """
                {"id":"in_1","amount_paid":99900,"currency":"inr","subscription":"sub_legacy"}
                """);

        assertThat(event.providerPaymentId()).isEqualTo("sub_legacy");
    }

    @Test
    void invoiceAggregationWindowIsNeverExposedAsSubscriptionPeriod() throws Exception {
        var event = verifiedEvent("invoice.paid", """
                {"id":"in_1","amount_paid":99900,"currency":"inr","subscription":"sub_123",
                 "period_start":1785522600,"period_end":1785522600}
                """);

        assertThat(event.status()).isEqualTo(PaymentWebhookStatus.SUCCEEDED);
        assertThat(event.providerPaymentId()).isEqualTo("sub_123");
        assertThat(event.periodStart()).isNull();
        assertThat(event.periodEnd()).isNull();
    }

    @Test
    void checkoutSessionPeriodLikeFieldsAreNotTreatedAsSubscriptionPeriod() throws Exception {
        var event = verifiedEvent("checkout.session.completed", """
                {"id":"cs_1","payment_status":"paid","subscription":"sub_123",
                 "amount_total":99900,"currency":"inr","current_period_start":1785522600,
                 "current_period_end":1788201000}
                """);

        assertThat(event.periodStart()).isNull();
        assertThat(event.periodEnd()).isNull();
    }

    @Test
    void subscriptionUpdatedSynchronizesWhileDeletedRemainsTerminalCancellation() throws Exception {
        var updated = verifiedEvent("customer.subscription.updated", """
                {"id":"sub_123","currency":"inr","current_period_start":1785522600,
                 "current_period_end":1788201000,"cancel_at_period_end":true,"status":"active"}
                """);
        var deleted = verifiedEvent("customer.subscription.deleted", """
                {"id":"sub_123","currency":"inr","cancel_at_period_end":false,"status":"canceled"}
                """);

        assertThat(updated.status()).isEqualTo(PaymentWebhookStatus.IGNORED);
        assertThat(updated.subscriptionStatus()).isEqualTo(ProviderSubscriptionStatus.ACTIVE);
        assertThat(updated.cancelAtPeriodEnd()).isTrue();
        assertThat(updated.periodStart()).isEqualTo(Instant.ofEpochSecond(1785522600));
        assertThat(updated.periodEnd()).isEqualTo(Instant.ofEpochSecond(1788201000));
        assertThat(deleted.status()).isEqualTo(PaymentWebhookStatus.CANCELED);
        assertThat(deleted.subscriptionStatus()).isEqualTo(ProviderSubscriptionStatus.CANCELED);
    }

    @Test
    void mapsNonEntitledSubscriptionStatusesWithoutReactivatingThem() throws Exception {
        var pastDue = verifiedEvent("customer.subscription.updated", """
                {"id":"sub_123","status":"past_due","cancel_at_period_end":false}
                """);
        var unpaid = verifiedEvent("customer.subscription.updated", """
                {"id":"sub_123","status":"unpaid","cancel_at_period_end":false}
                """);

        assertThat(pastDue.subscriptionStatus()).isEqualTo(ProviderSubscriptionStatus.PAST_DUE);
        assertThat(pastDue.status()).isEqualTo(PaymentWebhookStatus.FAILED);
        assertThat(unpaid.subscriptionStatus()).isEqualTo(ProviderSubscriptionStatus.UNPAID);
        assertThat(unpaid.status()).isEqualTo(PaymentWebhookStatus.FAILED);
    }

    @Test
    void buildsCheckoutReturnUrlsForEachRequestedWorkspaceWithoutConfiguredWorkspaceUrls() {
        UUID workspaceA = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID workspaceB = UUID.fromString("22222222-2222-2222-2222-222222222222");

        assertCheckoutUrls(workspaceA);
        assertCheckoutUrls(workspaceB);
    }

    @Test
    void checkoutConfigurationDoesNotRequireSuccessOrCancelUrlProperties() {
        StripeProperties properties = checkoutProperties();

        assertThat(properties.checkoutConfigured()).isTrue();
    }

    private void assertCheckoutUrls(UUID workspaceId) {
        StripeProperties properties = checkoutProperties();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String workspaceBillingUrl = "https://app.example.com/workspaces/" + workspaceId + "/billing";
        server.expect(requestTo("https://api.stripe.com/v1/checkout/sessions"))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("success_url=" + encoded(workspaceBillingUrl + "?checkout=success")),
                        org.hamcrest.Matchers.containsString("cancel_url=" + encoded(workspaceBillingUrl + "?checkout=cancel")))))
                .andRespond(withSuccess("""
                        {"id":"cs_1","url":"https://checkout.stripe.com/c/pay/cs_1","amount_total":99900}
                        """, MediaType.APPLICATION_JSON));
        StripePaymentGateway gateway = new StripePaymentGateway(properties, new ObjectMapper(), builder,
                "https://app.example.com");

        gateway.createCheckout(new CheckoutRequest(workspaceId, UUID.randomUUID(), Plan.PRO,
                99_900, "INR", "receipt-1"));

        server.verify();
    }

    private StripeProperties checkoutProperties() {
        StripeProperties properties = new StripeProperties();
        properties.setEnabled(true);
        properties.setSecretKey("secret");
        properties.setPublishableKey("key");
        properties.setProPriceId("price_1");
        return properties;
    }

    private String encoded(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String hmac(String secret, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private com.assetsphere.modules.billing.api.PaymentWebhookEvent verifiedEvent(String type, String object)
            throws Exception {
        StripeProperties properties = new StripeProperties();
        properties.setWebhookSecret("whsec_test");
        StripePaymentGateway gateway = new StripePaymentGateway(properties, new ObjectMapper(), RestClient.builder(),
                "https://app.example.com");
        long timestamp = Instant.now().getEpochSecond();
        String payload = "{\"id\":\"evt_1\",\"type\":\"" + type + "\",\"created\":" + timestamp
                + ",\"data\":{\"object\":" + object + "}}";
        return gateway.verifyWebhook(null, payload,
                "t=" + timestamp + ",v1=" + hmac("whsec_test", timestamp + "." + payload));
    }
}
