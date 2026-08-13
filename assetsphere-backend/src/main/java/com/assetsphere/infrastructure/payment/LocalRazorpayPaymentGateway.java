package com.assetsphere.infrastructure.payment;

import com.assetsphere.modules.billing.api.CheckoutRequest;
import com.assetsphere.modules.billing.api.CheckoutSession;
import com.assetsphere.modules.billing.api.PaymentGateway;
import com.assetsphere.modules.billing.api.PaymentProvider;
import com.assetsphere.modules.billing.api.PaymentWebhookEvent;
import com.assetsphere.modules.billing.api.PaymentWebhookStatus;
import com.assetsphere.modules.common.exception.InvalidRequestException;
import com.assetsphere.modules.common.exception.ServiceUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Order(0)
@ConditionalOnProperty(prefix = "assetsphere.billing", name = "payment-mode", havingValue = "RAZORPAY_LOCAL")
class LocalRazorpayPaymentGateway implements PaymentGateway {
    private final LocalRazorpayProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient client;

    LocalRazorpayPaymentGateway(LocalRazorpayProperties properties, ObjectMapper objectMapper, RestClient.Builder builder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.client = builder.clone().baseUrl(properties.getBaseUrl()).build();
    }

    @Override public PaymentProvider provider() { return PaymentProvider.RAZORPAY_LOCAL; }
    @Override public boolean supportsHostedCheckout() { return false; }
    @Override public String clientKeyId() { return null; }
    @Override public boolean available() { return properties.orderConfigured(); }

    @Override
    public CheckoutSession createCheckout(CheckoutRequest request) {
        properties.requireOrderConfiguration();
        try {
            LocalOrderRequest body = new LocalOrderRequest(
                    new LocalMoney(request.amountMinor(), request.currency()),
                    request.receipt(),
                    Map.of("workspaceId", request.workspaceId().toString(), "plan", request.plan().name()),
                    null,
                    null
            );
            JsonNode response = client.post().uri("/v1/orders")
                    .headers(headers -> {
                        headers.setBasicAuth(properties.getKeyId(), properties.getKeySecret());
                        headers.set("X-Idempotency-Key", request.receipt());
                    })
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
            LocalProviderOrder providerOrder = mapOrder(response, properties.requireVariant());
            String orderId = providerOrder.orderId();
            JsonNode amount = providerOrder.amount();
            long amountUnits = amount == null ? -1 : amount.path("amountUnits").asLong(-1);
            String currency = amount == null ? "" : amount.path("currency").asText();
            String status = providerOrder.status();
            if (orderId == null || status == null || amountUnits != request.amountMinor()
                    || !request.currency().equalsIgnoreCase(currency)) {
                throw new ServiceUnavailableException("Local Razorpay returned an inconsistent order response", null);
            }
            return new CheckoutSession(orderId, null, null, amountUnits, currency, status);
        } catch (ServiceUnavailableException exception) {
            throw exception;
        } catch (RestClientException | IllegalStateException exception) {
            throw new ServiceUnavailableException("Local Razorpay order creation is temporarily unavailable", exception);
        }
    }

    @Override
    public PaymentWebhookEvent verifyWebhook(String ignoredEventId, String payload, String signature) {
        properties.requireWebhookConfiguration();
        if (!validSignature(payload, signature)) throw new InvalidRequestException("Invalid local Razorpay webhook signature");
        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventType = root.path("event").asText();
            JsonNode data = root.path("payload");
            String orderId = text(data, "orderId");
            String paymentId = text(data, "paymentId");
            String paymentStatus = data.path("paymentStatus").asText();
            PaymentWebhookStatus status = status(eventType, paymentStatus);
            String eventId = stableEventId(eventType, orderId, paymentId, paymentStatus);
            return new PaymentWebhookEvent(provider(), eventId, eventType, orderId, paymentId,
                    data.path("amountUnits").asLong(0), data.path("amountCurrency").asText(""),
                    status, Instant.now(), true);
        } catch (InvalidRequestException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidRequestException("Invalid local Razorpay webhook payload");
        }
    }

    private PaymentWebhookStatus status(String eventType, String paymentStatus) {
        if ("ORDER_CANCELLED".equals(eventType)) return PaymentWebhookStatus.CANCELED;
        if (!"PAYMENT_STATUS_CHANGED".equals(eventType)) return PaymentWebhookStatus.IGNORED;
        return switch (paymentStatus) {
            case "CAPTURED", "SETTLED" -> PaymentWebhookStatus.SUCCEEDED;
            case "CANCELLED" -> PaymentWebhookStatus.CANCELED;
            case "FAILED", "AUTH_EXPIRED" -> PaymentWebhookStatus.FAILED;
            default -> PaymentWebhookStatus.IGNORED;
        };
    }

    private String stableEventId(String eventType, String orderId, String paymentId, String paymentStatus) {
        try {
            String value = String.join("|", safe(eventType), safe(orderId), safe(paymentId), safe(paymentStatus));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Local Razorpay event deduplication is unavailable", exception);
        }
    }

    private boolean validSignature(String payload, String signature) {
        if (signature == null || !signature.matches("[0-9a-fA-F]{64}")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    signature.toLowerCase().getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Local Razorpay webhook verification is unavailable", exception);
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null) return null;
        String value = node.path(field).asText();
        return value.isBlank() ? null : value;
    }

    private LocalProviderOrder mapOrder(JsonNode response, LocalRazorpayVariant variant) {
        if (response == null) return new LocalProviderOrder(null, null, null);
        return switch (variant) {
            case MY -> new LocalProviderOrder(text(response, "orderId"), response.path("amount"),
                    text(response, "orderStatus"));
            case TUTOR -> new LocalProviderOrder(text(response, "id"), response.path("amount"),
                    text(response, "status"));
        };
    }

    private String safe(String value) { return value == null ? "" : value; }

    record LocalMoney(long amountUnits, String currency) { }
    record LocalCustomer(String name, String email, String phone) { }
    record LocalOrderRequest(LocalMoney amount, String receipt, Map<String, String> notes,
                             Instant expiresAt, LocalCustomer customer) { }
    record LocalProviderOrder(String orderId, JsonNode amount, String status) { }
}
