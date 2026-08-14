package com.assetsphere.infrastructure.notification;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailConfigurationValidator {
    private final Environment environment;

    @PostConstruct
    public void validate() {
        EmailProvider selectedProvider = provider();
        if (!enabled()) return;
        switch (selectedProvider) {
            case SMTP -> {
                require("assetsphere.notification.email.from", "Invitation email sender address");
                require("spring.mail.host", "SMTP host");
            }
            case RESEND -> {
                require("assetsphere.notification.email.resend.api-key", "Resend API key");
                require("assetsphere.notification.email.resend.from", "Resend sender address");
            }
        }
    }

    public boolean enabled() {
        return environment.getProperty("assetsphere.notification.email.enabled", Boolean.class, false);
    }

    public boolean smtpSelected() {
        return enabled() && provider() == EmailProvider.SMTP;
    }

    private EmailProvider provider() {
        return EmailProvider.parse(environment.getProperty("assetsphere.notification.email.provider", "SMTP"));
    }

    private void require(String property, String label) {
        String value = environment.getProperty(property);
        if (value == null || value.isBlank()) throw new IllegalStateException(label + " is not configured");
    }
}
