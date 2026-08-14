package com.assetsphere.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NonSmtpMailHealthConfigurationTests {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NonSmtpMailHealthConfiguration.class);

    @Test
    void disabledEmailUsesNonSmtpHealthIndicator() {
        contextRunner.withPropertyValues("assetsphere.notification.email.enabled=false")
                .run(context -> assertThat(context).hasSingleBean(HealthIndicator.class));
    }

    @Test
    void resendUsesNonSmtpHealthIndicator() {
        contextRunner.withPropertyValues(
                        "assetsphere.notification.email.enabled=true",
                        "assetsphere.notification.email.provider=RESEND")
                .run(context -> assertThat(context).hasSingleBean(HealthIndicator.class));
    }

    @Test
    void smtpLeavesMailHealthToSpringBoot() {
        contextRunner.withPropertyValues(
                        "assetsphere.notification.email.enabled=true",
                        "assetsphere.notification.email.provider=SMTP")
                .run(context -> assertThat(context).doesNotHaveBean("mailHealthIndicator"));
    }
}
