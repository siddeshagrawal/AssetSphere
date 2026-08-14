package com.assetsphere.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.assetsphere.modules.workspace.api.WorkspaceInvitationEmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestClient;

class EmailTransportConfigurationTests {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(EmailTransportConfiguration.class)
            .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
            .withBean(RestClient.Builder.class, RestClient::builder);

    @Test
    void disabledEmailCreatesNoSendingTransport() {
        contextRunner.withPropertyValues("assetsphere.notification.email.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(WorkspaceInvitationEmailSender.class));
    }

    @Test
    void smtpSelectionCreatesOnlySmtpTransport() {
        contextRunner.withPropertyValues(
                        "assetsphere.notification.email.enabled=true",
                        "assetsphere.notification.email.provider=SMTP")
                .run(context -> {
                    assertThat(context).hasSingleBean(WorkspaceInvitationEmailSender.class);
                    assertThat(context.getBean(WorkspaceInvitationEmailSender.class))
                            .isInstanceOf(SmtpWorkspaceInvitationEmailSender.class);
                });
    }

    @Test
    void resendSelectionCreatesOnlyResendTransport() {
        contextRunner.withPropertyValues(
                        "assetsphere.notification.email.enabled=true",
                        "assetsphere.notification.email.provider=RESEND",
                        "assetsphere.notification.email.resend.api-key=test-key",
                        "assetsphere.notification.email.resend.from=notifications@example.com")
                .run(context -> {
                    assertThat(context).hasSingleBean(WorkspaceInvitationEmailSender.class);
                    assertThat(context.getBean(WorkspaceInvitationEmailSender.class))
                            .isInstanceOf(ResendWorkspaceInvitationEmailSender.class);
                });
    }
}
