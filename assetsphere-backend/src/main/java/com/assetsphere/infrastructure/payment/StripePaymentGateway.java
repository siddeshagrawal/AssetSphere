package com.assetsphere.infrastructure.payment;

import com.assetsphere.modules.billing.api.CheckoutRequest;
import com.assetsphere.modules.billing.api.CheckoutSession;
import com.assetsphere.modules.billing.api.PaymentGateway;
import com.assetsphere.modules.billing.api.PaymentProvider;
import com.assetsphere.modules.billing.api.PaymentWebhookEvent;
import com.assetsphere.modules.billing.api.PaymentWebhookStatus;
import com.assetsphere.modules.billing.api.ProviderSubscriptionStatus;
import com.assetsphere.modules.common.exception.InvalidRequestException;
import com.assetsphere.modules.common.exception.ServiceUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Order(0)
@ConditionalOnProperty(prefix = "assetsphere.billing", name = "payment-mode", havingValue = "STRIPE", matchIfMissing = true)
class StripePaymentGateway implements PaymentGateway {
    private static final long SIGNATURE_TOLERANCE_SECONDS = 300;
    private final StripeProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient client;
    private final String frontendBaseUrl;

    StripePaymentGateway(StripeProperties properties, ObjectMapper objectMapper, RestClient.Builder builder,
                         @Value("${assetsphere.notification.frontend-base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.client = builder.clone().baseUrl(properties.getBaseUrl()).build();
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override public PaymentProvider provider() { return PaymentProvider.STRIPE; }
    @Override public boolean supportsHostedCheckout() { return true; }
    @Override public String clientKeyId() { properties.requireCheckout(); return properties.getPublishableKey(); }
    @Override public boolean available() { return properties.checkoutConfigured() && properties.webhookConfigured()
            && StringUtils.hasText(frontendBaseUrl); }
    @Override public boolean supportsCancellation() { return true; }

    @Override
    public void cancelAtPeriodEnd(String externalSubscriptionId) {
        properties.requireCheckout();
        try {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("cancel_at_period_end", "true");
            JsonNode response = client.post().uri("/v1/subscriptions/{id}", externalSubscriptionId)
                    .headers(headers -> headers.setBearerAuth(properties.getSecretKey()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(JsonNode.class);
            if (response == null || !response.path("cancel_at_period_end").asBoolean(false)) {
                throw new ServiceUnavailableException("Stripe did not schedule subscription cancellation", null);
            }
        } catch (ServiceUnavailableException exception) {
            throw exception;
        } catch (RestClientException | IllegalStateException exception) {
            throw new ServiceUnavailableException("Stripe cancellation is temporarily unavailable", exception);
        }
    }

    @Override
    public CheckoutSession createCheckout(CheckoutRequest request) {
        properties.requireCheckout();
        if (!StringUtils.hasText(frontendBaseUrl)) {
            throw new ServiceUnavailableException("Stripe checkout redirect is not configured", null);
        }
        try {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("mode", "subscription");
            form.add("line_items[0][price]", properties.getProPriceId());
            form.add("line_items[0][quantity]", "1");
            form.add("success_url", checkoutReturnUrl(request, "success"));
            form.add("cancel_url", checkoutReturnUrl(request, "cancel"));
            form.add("client_reference_id", request.receipt());
            form.add("metadata[workspace_id]", request.workspaceId().toString());
            JsonNode response = client.post().uri("/v1/checkout/sessions")
                    .headers(headers -> headers.setBearerAuth(properties.getSecretKey()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(JsonNode.class);
            String id = text(response, "id");
            String url = text(response, "url");
            if (id == null || url == null || !url.startsWith("https://checkout.stripe.com/"))
                throw new ServiceUnavailableException("Stripe returned an invalid checkout session", null);
            if (response.hasNonNull("amount_total") && response.path("amount_total").asLong() != request.amountMinor())
                throw new ServiceUnavailableException("Stripe returned inconsistent checkout pricing", null);
            return new CheckoutSession(id, properties.getPublishableKey(), url, request.amountMinor(), request.currency(), null);
        } catch (ServiceUnavailableException exception) {
            throw exception;
        } catch (RestClientException | IllegalStateException exception) {
            throw new ServiceUnavailableException("Stripe checkout is temporarily unavailable", exception);
        }
    }

    private String checkoutReturnUrl(CheckoutRequest request, String outcome) {
        return UriComponentsBuilder.fromUriString(frontendBaseUrl)
                .pathSegment("workspaces", request.workspaceId().toString(), "billing")
                .queryParam("checkout", outcome)
                .build().encode().toUriString();
    }

    @Override
    public PaymentWebhookEvent verifyWebhook(String eventId, String payload, String signature) {
        properties.requireWebhook();
        Signature parsed = signature(signature);
        if (Math.abs(Instant.now().getEpochSecond() - parsed.timestamp()) > SIGNATURE_TOLERANCE_SECONDS
                || !valid(parsed.timestamp() + "." + payload, parsed.value()))
            throw new InvalidRequestException("Invalid Stripe webhook signature");
        try {
            JsonNode root = objectMapper.readTree(payload);
            String type = root.path("type").asText();
            JsonNode session = root.path("data").path("object");
            ProviderSubscriptionStatus subscriptionStatus = stripeSubscriptionStatus(type, session);
            PaymentWebhookStatus status = switch (type) {
                case "checkout.session.completed", "checkout.session.async_payment_succeeded" -> "paid".equals(session.path("payment_status").asText())
                        ? PaymentWebhookStatus.SUCCEEDED : PaymentWebhookStatus.IGNORED;
                case "checkout.session.expired", "checkout.session.async_payment_failed" -> PaymentWebhookStatus.FAILED;
                case "customer.subscription.updated" -> subscriptionWebhookStatus(subscriptionStatus);
                case "customer.subscription.deleted" -> PaymentWebhookStatus.CANCELED;
                case "invoice.paid" -> PaymentWebhookStatus.SUCCEEDED;
                case "invoice.payment_failed" -> PaymentWebhookStatus.FAILED;
                default -> PaymentWebhookStatus.IGNORED;
            };
            String paymentId = stripeSubscriptionId(type, session);
            String orderId = type.startsWith("checkout.session") ? text(session, "id") : null;
            String actualEventId = root.path("id").asText(eventId);
            long amount = type.startsWith("invoice.") ? session.path("amount_paid").asLong(0)
                    : session.path("amount_total").asLong(0);
            Instant periodStart = type.startsWith("invoice.")
                    ? epoch(session, "period_start") : epoch(session, "current_period_start");
            Instant periodEnd = type.startsWith("invoice.")
                    ? epoch(session, "period_end") : epoch(session, "current_period_end");
            Boolean cancelAtPeriodEnd = type.startsWith("customer.subscription")
                    ? session.path("cancel_at_period_end").asBoolean(false) : null;
            Instant occurredAt = epoch(root, "created");
            if (occurredAt == null) throw new IllegalArgumentException("Stripe event timestamp is required");
            return new PaymentWebhookEvent(provider(), actualEventId, type, orderId, paymentId,
                    amount, session.path("currency").asText("").toUpperCase(),
                    status, occurredAt, true,
                    periodStart, periodEnd, cancelAtPeriodEnd, subscriptionStatus);
        } catch (Exception exception) {
            throw new InvalidRequestException("Invalid Stripe webhook payload");
        }
    }

    private Signature signature(String header) {
        long timestamp = -1; String value = null;
        if (header != null) for (String part : header.split(",")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && pair[0].equals("t")) try { timestamp = Long.parseLong(pair[1]); } catch (NumberFormatException ignored) { }
            if (pair.length == 2 && pair[0].equals("v1")) value = pair[1];
        }
        if (timestamp < 0 || value == null || !value.matches("[0-9a-fA-F]{64}")) throw new InvalidRequestException("Invalid Stripe webhook signature");
        return new Signature(timestamp, value);
    }

    private boolean valid(String signedPayload, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), signature.toLowerCase().getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException exception) { throw new IllegalStateException("Stripe webhook verification is unavailable", exception); }
    }

    private String text(JsonNode node, String field) { if (node == null) return null; String value = node.path(field).asText(); return value.isBlank() ? null : value; }
    private String stripeSubscriptionId(String eventType, JsonNode object) {
        if (eventType.startsWith("customer.subscription.")) return text(object, "id");
        if (eventType.startsWith("checkout.session.")) return identifier(object == null ? null : object.get("subscription"));
        if (eventType.startsWith("invoice.")) {
            JsonNode parent = object == null ? null : object.path("parent");
            String nested = parent != null && "subscription_details".equals(text(parent, "type"))
                    ? identifier(parent.path("subscription_details").get("subscription")) : null;
            return first(nested, identifier(object == null ? null : object.get("subscription")));
        }
        return null;
    }
    private String identifier(JsonNode value) {
        if (value == null || value.isNull()) return null;
        return value.isObject() ? text(value, "id") : value.asText().isBlank() ? null : value.asText();
    }
    private Instant epoch(JsonNode node, String field) {
        long value = node == null ? 0 : node.path(field).asLong(0);
        return value > 0 ? Instant.ofEpochSecond(value) : null;
    }
    private String first(String first, String second) { return first == null ? second : first; }
    private ProviderSubscriptionStatus stripeSubscriptionStatus(String eventType, JsonNode object) {
        if (!eventType.startsWith("customer.subscription.")) return null;
        return switch (object.path("status").asText("").toLowerCase()) {
            case "active" -> ProviderSubscriptionStatus.ACTIVE;
            case "trialing" -> ProviderSubscriptionStatus.TRIALING;
            case "past_due" -> ProviderSubscriptionStatus.PAST_DUE;
            case "unpaid" -> ProviderSubscriptionStatus.UNPAID;
            case "canceled" -> ProviderSubscriptionStatus.CANCELED;
            case "incomplete" -> ProviderSubscriptionStatus.INCOMPLETE;
            case "incomplete_expired" -> ProviderSubscriptionStatus.INCOMPLETE_EXPIRED;
            case "paused" -> ProviderSubscriptionStatus.PAUSED;
            default -> ProviderSubscriptionStatus.UNKNOWN;
        };
    }
    private PaymentWebhookStatus subscriptionWebhookStatus(ProviderSubscriptionStatus status) {
        if (status == null || status == ProviderSubscriptionStatus.UNKNOWN) return PaymentWebhookStatus.IGNORED;
        if (status.terminal()) return PaymentWebhookStatus.CANCELED;
        return status.entitled() ? PaymentWebhookStatus.IGNORED : PaymentWebhookStatus.FAILED;
    }
    private record Signature(long timestamp, String value) { }
}
