package com.assetsphere.infrastructure.payment;

import com.assetsphere.modules.billing.api.LocalPaymentDemoGateway;
import com.assetsphere.modules.billing.api.LocalPaymentMethod;
import com.assetsphere.modules.common.exception.ResourceNotFoundException;
import com.assetsphere.modules.common.exception.ServiceUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(prefix = "assetsphere.billing", name = "payment-mode", havingValue = "RAZORPAY_LOCAL")
class LocalRazorpayPaymentClient implements LocalPaymentDemoGateway {
    private static final long TOKEN_TTL_SECONDS = 600;
    private final LocalRazorpayProperties properties;
    private final RestClient client;
    private final Clock clock;
    private final Map<String, CachedPayment> cache = new ConcurrentHashMap<>();
    private final Map<String, CachedToken> cardTokens = new ConcurrentHashMap<>();

    @Autowired
    LocalRazorpayPaymentClient(LocalRazorpayProperties properties, RestClient.Builder builder) {
        this(properties, builder, Clock.systemUTC());
    }

    LocalRazorpayPaymentClient(LocalRazorpayProperties properties, RestClient.Builder builder, Clock clock) {
        this.properties = properties;
        this.client = builder.clone().baseUrl(properties.getBaseUrl()).build();
        this.clock = clock;
    }

    @Override
    public LocalPaymentResult create(String orderId, LocalPaymentMethod method, Map<String, String> methodDetails,
                                     String idempotencyKey) {
        properties.requireOrderConfiguration();
        try {
            Map<String, String> providerDetails = method == LocalPaymentMethod.CARD
                    ? Map.of("token", cardToken(methodDetails, idempotencyKey)) : methodDetails;
            JsonNode response = client.post().uri("/v1/payments")
                    .headers(headers -> {
                        headers.setBasicAuth(properties.getKeyId(), properties.getKeySecret());
                        headers.set("X-Idempotency-Key", idempotencyKey);
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ProviderPaymentRequest(orderId, method.name(), providerDetails))
                    .retrieve().body(JsonNode.class);
            LocalPaymentResult result = map(response);
            if (method == LocalPaymentMethod.CARD) cardTokens.remove(idempotencyKey);
            cache(result);
            return result;
        } catch (RestClientResponseException exception) {
            throw providerFailure(exception);
        } catch (RestClientException | IllegalStateException exception) {
            throw new ServiceUnavailableException("Local payment service is temporarily unavailable", exception);
        }
    }

    @Override
    public boolean pollConfirmationEnabled() {
        return properties.isPollConfirmationEnabled()
                && properties.requireVariant() == LocalRazorpayVariant.MY;
    }

    @Override
    public boolean cardEnabled() {
        return properties.requireVariant() == LocalRazorpayVariant.MY;
    }

    private String cardToken(Map<String, String> details, String idempotencyKey) {
        CachedToken cached = cardTokens.get(idempotencyKey);
        if (cached != null && clock.instant().isBefore(cached.expiresAt())) return cached.token();
        JsonNode response = client.post().uri("/v1/vault/tokenize")
                .headers(headers -> {
                    headers.setBasicAuth(properties.getKeyId(), properties.getKeySecret());
                    headers.set("X-Idempotency-Key", idempotencyKey + "-vault");
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ProviderTokenizeRequest(details.get("PAN"), details.get("CVV"),
                        Integer.valueOf(details.get("EXPIRY_MONTH")), Integer.valueOf(details.get("EXPIRY_YEAR")),
                        null, details.get("CARD_HOLDER_NAME")))
                .retrieve().body(JsonNode.class);
        String token = text(response, "token");
        if (token == null) {
            throw new ServiceUnavailableException("Local payment service returned an invalid card token", null);
        }
        cardTokens.put(idempotencyKey, new CachedToken(token, clock.instant().plusSeconds(TOKEN_TTL_SECONDS)));
        return token;
    }

    @Override
    public LocalPaymentResult get(String orderId, String paymentId) {
        CachedPayment cached = cache.get(paymentId);
        if (cached != null && clock.instant().isBefore(cached.refreshAfter())) return cached.result();
        properties.requireOrderConfiguration();
        try {
            JsonNode response = client.get().uri("/v1/orders/{orderId}/payments", orderId)
                    .headers(headers -> headers.setBasicAuth(properties.getKeyId(), properties.getKeySecret()))
                    .retrieve().body(JsonNode.class);
            if (response == null || !response.isArray()) {
                throw new ServiceUnavailableException("Local payment service returned an invalid status response", null);
            }
            for (JsonNode payment : response) {
                if (paymentId.equals(text(payment, "id"))) {
                    LocalPaymentResult result = map(payment);
                    cache(result);
                    return result;
                }
            }
            throw new ResourceNotFoundException("Provider payment was not found");
        } catch (ResourceNotFoundException | ServiceUnavailableException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 429 && cached != null) {
                cache.put(paymentId, new CachedPayment(cached.result(), clock.instant().plusSeconds(retryAfter(exception))));
                return cached.result();
            }
            throw providerFailure(exception);
        } catch (RestClientException | IllegalStateException exception) {
            throw new ServiceUnavailableException("Local payment service is temporarily unavailable", exception);
        }
    }

    private RuntimeException providerFailure(RestClientResponseException exception) {
        if (exception.getStatusCode().value() == 429) {
            return new ServiceUnavailableException("Payment service is temporarily busy. Please retry shortly.", exception);
        }
        return new ServiceUnavailableException("Local payment service is temporarily unavailable", exception);
    }

    private long retryAfter(RestClientResponseException exception) {
        String value = exception.getResponseHeaders() == null ? null
                : exception.getResponseHeaders().getFirst("Retry-After");
        try {
            return value == null ? 60 : Math.max(1, Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return 60;
        }
    }

    private void cache(LocalPaymentResult result) {
        var ttl = terminal(result.providerPaymentStatus())
                ? properties.getTerminalStatusCacheTtl()
                : properties.getNonTerminalStatusCacheTtl();
        if (ttl == null || ttl.isZero() || ttl.isNegative()) ttl = java.time.Duration.ofSeconds(1);
        cache.put(result.paymentId(), new CachedPayment(result, clock.instant().plus(ttl)));
    }

    private LocalPaymentResult map(JsonNode response) {
        String paymentId = text(response, "id");
        String orderId = text(response, "orderId");
        String status = text(response, "status");
        String methodValue = text(response, "method");
        JsonNode amount = response == null ? null : response.path("amount");
        long amountMinor = amount == null ? -1 : amount.path("amountUnits").asLong(-1);
        String currency = amount == null ? null : text(amount, "currency");
        if (paymentId == null || orderId == null || status == null || methodValue == null
                || amountMinor < 0 || currency == null) {
            throw new ServiceUnavailableException("Local payment service returned an invalid payment response", null);
        }
        try {
            String createdAt = text(response, "createdAt");
            Instant created = createdAt == null ? clock.instant()
                    : LocalDateTime.parse(createdAt).toInstant(ZoneOffset.UTC);
            return new LocalPaymentResult(paymentId, orderId, status, LocalPaymentMethod.valueOf(methodValue),
                    amountMinor, currency, created);
        } catch (RuntimeException exception) {
            throw new ServiceUnavailableException("Local payment service returned an invalid payment response", exception);
        }
    }

    private boolean terminal(String status) {
        return switch (status) {
            case "CAPTURED", "SETTLED", "FAILED", "CANCELLED", "AUTH_EXPIRED" -> true;
            default -> false;
        };
    }

    private String text(JsonNode node, String field) {
        if (node == null) return null;
        String value = node.path(field).asText();
        return value.isBlank() ? null : value;
    }

    record ProviderPaymentRequest(String orderId, String method, Map<String, String> methodDetails) { }
    record ProviderTokenizeRequest(String pan, String cvv, Integer expiryMonth, Integer expiryYear,
                                   String customerId, String cardHolderName) { }
    record CachedPayment(LocalPaymentResult result, Instant refreshAfter) { }
    record CachedToken(String token, Instant expiresAt) { }
}
