package com.assetsphere.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class EmailConfigurationValidatorTests {
    @Test
    void disabledEmailRequiresNoTransportConfiguration() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("assetsphere.notification.email.enabled", "false");

        assertThatCode(new EmailConfigurationValidator(environment)::validate).doesNotThrowAnyException();
    }

    @Test
    void smtpRequiresSenderAndHost() {
        MockEnvironment environment = smtpEnvironment();
        assertThatCode(new EmailConfigurationValidator(environment)::validate).doesNotThrowAnyException();

        environment.setProperty("assetsphere.notification.email.from", " ");
        assertThatThrownBy(new EmailConfigurationValidator(environment)::validate)
                .hasMessage("Invitation email sender address is not configured");

        environment.setProperty("assetsphere.notification.email.from", "notifications@example.com");
        environment.setProperty("spring.mail.host", " ");
        assertThatThrownBy(new EmailConfigurationValidator(environment)::validate)
                .hasMessage("SMTP host is not configured");
    }

    @Test
    void resendRequiresApiKeyAndSender() {
        MockEnvironment environment = resendEnvironment();
        assertThatCode(new EmailConfigurationValidator(environment)::validate).doesNotThrowAnyException();

        environment.setProperty("assetsphere.notification.email.resend.api-key", " ");
        assertThatThrownBy(new EmailConfigurationValidator(environment)::validate)
                .hasMessage("Resend API key is not configured");

        environment.setProperty("assetsphere.notification.email.resend.api-key", "test-key");
        environment.setProperty("assetsphere.notification.email.resend.from", " ");
        assertThatThrownBy(new EmailConfigurationValidator(environment)::validate)
                .hasMessage("Resend sender address is not configured");
    }

    @Test
    void unknownProviderFailsClearly() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("assetsphere.notification.email.enabled", "true")
                .withProperty("assetsphere.notification.email.provider", "UNKNOWN");

        assertThatThrownBy(new EmailConfigurationValidator(environment)::validate)
                .hasMessage("Unknown email provider; expected SMTP or RESEND");
    }

    private MockEnvironment smtpEnvironment() {
        return new MockEnvironment()
                .withProperty("assetsphere.notification.email.enabled", "true")
                .withProperty("assetsphere.notification.email.provider", "SMTP")
                .withProperty("assetsphere.notification.email.from", "notifications@example.com")
                .withProperty("spring.mail.host", "smtp.example.com");
    }

    private MockEnvironment resendEnvironment() {
        return new MockEnvironment()
                .withProperty("assetsphere.notification.email.enabled", "true")
                .withProperty("assetsphere.notification.email.provider", "RESEND")
                .withProperty("assetsphere.notification.email.resend.api-key", "test-key")
                .withProperty("assetsphere.notification.email.resend.from", "notifications@example.com");
    }
}
