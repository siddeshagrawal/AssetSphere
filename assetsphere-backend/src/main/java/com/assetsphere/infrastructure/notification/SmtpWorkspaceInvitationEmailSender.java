package com.assetsphere.infrastructure.notification;

import com.assetsphere.modules.workspace.api.WorkspaceInvitationEmailSender;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "assetsphere.notification.email", name = "enabled", havingValue = "true")
class SmtpWorkspaceInvitationEmailSender implements WorkspaceInvitationEmailSender {
    private final JavaMailSender mailSender;
    private final String from;
    private final DateTimeFormatter expiryFormatter;

    SmtpWorkspaceInvitationEmailSender(
            JavaMailSender mailSender,
            @Value("${assetsphere.notification.email.from:}") String from,
            @Value("${assetsphere.notification.email.time-zone:Asia/Kolkata}") String timeZone) {
        this.mailSender = mailSender;
        this.from = from;
        this.expiryFormatter = DateTimeFormatter.ofPattern("dd MMM uuuu, h:mm a z", Locale.ENGLISH)
                .withZone(ZoneId.of(timeZone));
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
            helper.setSubject("You’re invited to " + invitation.workspaceName() + " on AssetSphere");
            helper.setText(body(invitation), false);
            mailSender.send(message);
        } catch (Exception exception) {
            throw new IllegalStateException("Workspace invitation email delivery failed", exception);
        }
    }

    private String body(InvitationEmail invitation) {
        return """
                You’ve been invited to join %s on AssetSphere.

                Invited by: %s
                Role: %s
                Invitation expires: %s

                Review and respond to this secure, single-use invitation:
                %s

                If you were not expecting this invitation, you can ignore this email.
                """.formatted(invitation.workspaceName(), invitation.inviterEmail(), invitation.role(),
                formatExpiry(invitation.expiresAt()), invitation.acceptanceUrl());
    }

    String formatExpiry(Instant expiresAt) {
        return expiryFormatter.format(expiresAt);
    }
}
