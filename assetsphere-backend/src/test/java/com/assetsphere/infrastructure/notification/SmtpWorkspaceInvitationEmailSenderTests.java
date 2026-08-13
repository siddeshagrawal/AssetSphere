package com.assetsphere.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Instant;
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
}
