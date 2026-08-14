package com.assetsphere.infrastructure.notification;

import com.assetsphere.modules.workspace.api.WorkspaceInvitationEmailSender;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

class SmtpWorkspaceInvitationEmailSender implements WorkspaceInvitationEmailSender {
    private final JavaMailSender mailSender;
    private final String from;
    private final InvitationEmailContent content;

    SmtpWorkspaceInvitationEmailSender(
            JavaMailSender mailSender,
            String from,
            String timeZone) {
        this.mailSender = mailSender;
        this.from = from;
        this.content = new InvitationEmailContent(timeZone);
    }

    @Override
    public void send(InvitationEmail invitation) {
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("Invitation email sender address is not configured");
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(invitation.recipientEmail());
            helper.setSubject(content.subject(invitation));
            helper.setText(content.text(invitation), false);
            mailSender.send(message);
        } catch (Exception exception) {
            throw new IllegalStateException("Workspace invitation email delivery failed", exception);
        }
    }

    String formatExpiry(Instant expiresAt) {
        return content.formatExpiry(expiresAt);
    }
}
