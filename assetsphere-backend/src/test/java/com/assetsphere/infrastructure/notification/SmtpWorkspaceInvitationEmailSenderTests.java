package com.assetsphere.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetsphere.modules.workspace.api.WorkspaceInvitationEmailSender.InvitationEmail;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpWorkspaceInvitationEmailSenderTests {
    @Test
    void formatsInvitationExpiryInConfiguredTimeZone() {
        SmtpWorkspaceInvitationEmailSender sender = new SmtpWorkspaceInvitationEmailSender(
                mock(JavaMailSender.class), "notifications@assetsphere.test", "Asia/Kolkata");

        assertThat(sender.formatExpiry(Instant.parse("2026-08-18T14:11:44Z")))
                .isEqualTo("18 Aug 2026, 7:41 PM IST");
    }

    @Test
    void sendsInvitationThroughJavaMailSender() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        SmtpWorkspaceInvitationEmailSender sender = new SmtpWorkspaceInvitationEmailSender(
                mailSender, "notifications@assetsphere.test", "Asia/Kolkata");

        sender.send(new InvitationEmail("recipient@example.com", "owner@example.com", "Engineering",
                "MEMBER", Instant.parse("2026-08-18T14:11:44Z"), "https://app.example.com/invite/test"));

        verify(mailSender).send(message);
        assertThat(message.getSubject()).isEqualTo("You\u2019re invited to Engineering on AssetSphere");
        assertThat(message.getContent().toString()).contains("owner@example.com", "MEMBER",
                "https://app.example.com/invite/test");
    }
}
