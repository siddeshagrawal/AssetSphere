package com.assetsphere.infrastructure.notification;

import com.assetsphere.modules.workspace.api.WorkspaceInvitationEmailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "assetsphere.notification.email", name = "enabled", havingValue = "true")
class EmailTransportConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "assetsphere.notification.email", name = "provider",
            havingValue = "SMTP", matchIfMissing = true)
    WorkspaceInvitationEmailSender smtpWorkspaceInvitationEmailSender(
            JavaMailSender mailSender,
            @Value("${assetsphere.notification.email.from:}") String from,
            @Value("${assetsphere.notification.email.time-zone:Asia/Kolkata}") String timeZone) {
        return new SmtpWorkspaceInvitationEmailSender(mailSender, from, timeZone);
    }

    @Bean
    @ConditionalOnProperty(prefix = "assetsphere.notification.email", name = "provider", havingValue = "RESEND")
    WorkspaceInvitationEmailSender resendWorkspaceInvitationEmailSender(
            RestClient.Builder builder,
            @Value("${assetsphere.notification.email.resend.api-key:}") String apiKey,
            @Value("${assetsphere.notification.email.resend.from:}") String from,
            @Value("${assetsphere.notification.email.time-zone:Asia/Kolkata}") String timeZone) {
        return new ResendWorkspaceInvitationEmailSender(builder, apiKey, from, timeZone);
    }
}
