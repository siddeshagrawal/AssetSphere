package com.assetsphere.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetsphere.modules.auth.application.GoogleOAuthProperties;
import com.assetsphere.infrastructure.notification.EmailConfigurationValidator;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class ProductionConfigurationTests {
    @ParameterizedTest
    @ValueSource(strings = {
            "spring.datasource.url", "spring.datasource.username", "spring.datasource.password",
            "spring.kafka.bootstrap-servers", "assetsphere.cors.allowed-origins", "assetsphere.jwt.secret",
            "assetsphere.billing.stripe.secret-key", "assetsphere.billing.stripe.publishable-key",
            "assetsphere.billing.stripe.pro-price-id", "assetsphere.billing.stripe.webhook-secret",
            "assetsphere.notification.frontend-base-url"
    })
    void requiredProductionPropertyMustBeConfigured(String property) {
        Fixture fixture = validFixture();
        fixture.environment.setProperty(property, " ");

        assertThatThrownBy(fixture.validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void localRazorpayIsForbiddenAndStripeMustBeEnabled() {
        Fixture local = validFixture();
        local.environment.setProperty("assetsphere.billing.payment-mode", "RAZORPAY_LOCAL");
        assertThatThrownBy(local.validator::validate)
                .hasMessage("Local Razorpay is not permitted in production");

        Fixture disabledStripe = validFixture();
        disabledStripe.environment.setProperty("assetsphere.billing.stripe.enabled", "false");
        assertThatThrownBy(disabledStripe.validator::validate)
                .hasMessage("Selected payment provider STRIPE is disabled");
    }

    @ParameterizedTest
    @ValueSource(strings = {"endpoint", "accessKey", "secretKey", "bucket"})
    void enabledObjectStorageRequiresEveryConnectionField(String field) {
        Fixture fixture = validFixture();
        mutateMinio(fixture.properties, field, " ");

        assertThatThrownBy(fixture.validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Object storage");
    }

    @Test
    void enabledObjectStorageAllowsAutoCreateDisabled() {
        Fixture fixture = validFixture();
        fixture.properties.getStorage().getMinio().setAutoCreateBucket(false);

        assertThatCode(fixture.validator::validate).doesNotThrowAnyException();
    }

    @Test
    void disabledObjectStorageDoesNotRequireConnectionFields() {
        Fixture fixture = validFixture();
        var minio = fixture.properties.getStorage().getMinio();
        minio.setEnabled(false);
        minio.setEndpoint("");
        minio.setAccessKey("");
        minio.setSecretKey("");
        minio.setBucket("");

        assertThatCode(fixture.validator::validate).doesNotThrowAnyException();
    }

    @Test
    void googleOAuthIsOptionalButValidatedWhenEnabled() {
        Fixture disabled = validFixture();
        disabled.google.setEnabled(false);
        assertThatCode(disabled.validator::validate).doesNotThrowAnyException();

        Fixture enabled = validFixture();
        enabled.google.setEnabled(true);
        enabled.google.setClientSecret("");
        assertThatThrownBy(enabled.validator::validate)
                .hasMessage("Google OAuth is enabled but not fully configured");
    }

    @ParameterizedTest
    @ValueSource(strings = {"clientId", "clientSecret", "successUrl", "failureUrl"})
    void enabledGoogleOAuthRequiresEveryCredentialAndRedirect(String field) {
        Fixture fixture = validFixture();
        fixture.google.setEnabled(true);
        switch (field) {
            case "clientId" -> fixture.google.setClientId("");
            case "clientSecret" -> fixture.google.setClientSecret("");
            case "successUrl" -> fixture.google.setFrontendSuccessUrl("");
            case "failureUrl" -> fixture.google.setFrontendFailureUrl("");
            default -> throw new IllegalArgumentException(field);
        }

        assertThatThrownBy(fixture.validator::validate)
                .hasMessage("Google OAuth is enabled but not fully configured");
    }

    @Test
    void emailIsOptionalButRequiresSenderAndNonLocalSmtpHostWhenEnabled() {
        Fixture disabled = validFixture();
        disabled.environment.setProperty("assetsphere.notification.email.enabled", "false");
        disabled.environment.setProperty("assetsphere.notification.email.from", "");
        disabled.environment.setProperty("spring.mail.host", "");
        assertThatCode(disabled.validator::validate).doesNotThrowAnyException();

        Fixture missingSender = validFixture();
        missingSender.environment.setProperty("assetsphere.notification.email.enabled", "true");
        missingSender.environment.setProperty("assetsphere.notification.email.from", "");
        assertThatThrownBy(missingSender.validator::validate).hasMessageContaining("sender address");

        Fixture missingHost = validFixture();
        missingHost.environment.setProperty("assetsphere.notification.email.enabled", "true");
        missingHost.environment.setProperty("spring.mail.host", "");
        assertThatThrownBy(missingHost.validator::validate).hasMessageContaining("SMTP host");

        Fixture localHost = validFixture();
        localHost.environment.setProperty("assetsphere.notification.email.enabled", "true");
        localHost.environment.setProperty("spring.mail.host", "localhost");
        assertThatThrownBy(localHost.validator::validate).hasMessageContaining("local development address");
    }

    @Test
    void aiIsOptionalButRequiresOpenAiKeyWhenEnabled() {
        Fixture disabled = validFixture();
        disabled.environment.setProperty("assetsphere.ai.enabled", "false");
        disabled.environment.setProperty("spring.ai.openai.api-key", "");
        assertThatCode(disabled.validator::validate).doesNotThrowAnyException();

        Fixture enabled = validFixture();
        enabled.environment.setProperty("assetsphere.ai.enabled", "true");
        enabled.environment.setProperty("spring.ai.openai.api-key", "");
        assertThatThrownBy(enabled.validator::validate).hasMessageContaining("OpenAI API key");
    }

    @Test
    void completeProductionConfigurationSucceeds() {
        assertThatCode(validFixture().validator::validate).doesNotThrowAnyException();
    }

    @Test
    void productionProfileRetainsStaticSafetyDefaults() throws Exception {
        try (var stream = getClass().getResourceAsStream("/application-prod.yml")) {
            assertThat(stream).isNotNull();
            String configuration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(configuration).doesNotContain("localhost", "127.0.0.1", "minioadmin");
            assertThat(configuration).contains("forward-headers-strategy: framework")
                    .contains("enabled: false")
                    .contains("auto-create-bucket: ${MINIO_AUTO_CREATE_BUCKET:false}");
        }
    }

    private Fixture validFixture() {
        MockEnvironment environment = new MockEnvironment();
        set(environment, "spring.datasource.url", "jdbc:postgresql://db.example/assetsphere");
        set(environment, "spring.datasource.username", "assetsphere");
        set(environment, "spring.datasource.password", "database-secret");
        set(environment, "spring.kafka.bootstrap-servers", "broker.example:9093");
        set(environment, "assetsphere.cors.allowed-origins", "https://app.example.com");
        set(environment, "assetsphere.jwt.secret", "a-production-jwt-secret-at-least-32-bytes");
        set(environment, "assetsphere.billing.payment-mode", "STRIPE");
        set(environment, "assetsphere.billing.stripe.enabled", "true");
        set(environment, "assetsphere.billing.stripe.secret-key", "stripe-secret");
        set(environment, "assetsphere.billing.stripe.publishable-key", "stripe-public");
        set(environment, "assetsphere.billing.stripe.pro-price-id", "price_pro");
        set(environment, "assetsphere.billing.stripe.webhook-secret", "webhook-secret");
        set(environment, "assetsphere.notification.frontend-base-url", "https://app.example.com");
        set(environment, "assetsphere.notification.email.enabled", "false");
        set(environment, "assetsphere.notification.email.provider", "SMTP");
        set(environment, "assetsphere.notification.email.from", "notifications@example.com");
        set(environment, "assetsphere.notification.email.resend.api-key", "resend-secret");
        set(environment, "assetsphere.notification.email.resend.from", "notifications@example.com");
        set(environment, "spring.mail.host", "smtp.example.com");
        set(environment, "assetsphere.ai.enabled", "false");
        set(environment, "spring.ai.openai.api-key", "openai-secret");

        ApplicationProperties properties = new ApplicationProperties();
        var minio = properties.getStorage().getMinio();
        minio.setEnabled(true);
        minio.setEndpoint("https://objects.example.com");
        minio.setAccessKey("storage-key");
        minio.setSecretKey("storage-secret");
        minio.setBucket("assetsphere");
        minio.setAutoCreateBucket(false);

        GoogleOAuthProperties google = new GoogleOAuthProperties();
        google.setEnabled(false);
        google.setClientId("google-client");
        google.setClientSecret("google-secret");
        google.setFrontendSuccessUrl("https://app.example.com/oauth/callback");
        google.setFrontendFailureUrl("https://app.example.com/login");
        return new Fixture(environment, properties, google,
                new ProductionConfigurationValidator(environment, properties,
                        () -> {
                            if (google.isEnabled()) google.requireConfigured();
                        }, new EmailConfigurationValidator(environment)));
    }

    private void mutateMinio(ApplicationProperties properties, String field, String value) {
        var minio = properties.getStorage().getMinio();
        Consumer<String> setter = switch (field) {
            case "endpoint" -> minio::setEndpoint;
            case "accessKey" -> minio::setAccessKey;
            case "secretKey" -> minio::setSecretKey;
            case "bucket" -> minio::setBucket;
            default -> throw new IllegalArgumentException(field);
        };
        setter.accept(value);
    }

    private void set(MockEnvironment environment, String property, String value) {
        environment.setProperty(property, value);
    }

    private record Fixture(MockEnvironment environment, ApplicationProperties properties,
                           GoogleOAuthProperties google, ProductionConfigurationValidator validator) { }
}
