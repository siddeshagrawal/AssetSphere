package com.assetsphere.infrastructure.notification;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Configuration(proxyBeanMethods = false)
@Conditional(NonSmtpMailHealthConfiguration.NonSmtpMailCondition.class)
class NonSmtpMailHealthConfiguration {
    @Bean(name = "mailHealthIndicator")
    HealthIndicator mailHealthIndicator() {
        return () -> Health.up().withDetail("transport", "not-smtp").build();
    }

    static class NonSmtpMailCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            boolean enabled = context.getEnvironment()
                    .getProperty("assetsphere.notification.email.enabled", Boolean.class, false);
            EmailProvider provider = EmailProvider.parse(context.getEnvironment()
                    .getProperty("assetsphere.notification.email.provider", "SMTP"));
            return !enabled || provider != EmailProvider.SMTP;
        }
    }
}
