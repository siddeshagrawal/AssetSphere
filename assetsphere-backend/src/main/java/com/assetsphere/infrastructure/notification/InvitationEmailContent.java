package com.assetsphere.infrastructure.notification;

import com.assetsphere.modules.workspace.api.WorkspaceInvitationEmailSender.InvitationEmail;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.web.util.HtmlUtils;

final class InvitationEmailContent {
    private final DateTimeFormatter expiryFormatter;

    InvitationEmailContent(String timeZone) {
        expiryFormatter = DateTimeFormatter.ofPattern("dd MMM uuuu, h:mm a z", Locale.ENGLISH)
                .withZone(ZoneId.of(timeZone));
    }

    String subject(InvitationEmail invitation) {
        return "You\u2019re invited to " + invitation.workspaceName() + " on AssetSphere";
    }

    String text(InvitationEmail invitation) {
        return """
                You\u2019ve been invited to join %s on AssetSphere.

                Invited by: %s
                Role: %s
                Invitation expires: %s

                Review and respond to this secure, single-use invitation:
                %s

                If you were not expecting this invitation, you can ignore this email.
                """.formatted(invitation.workspaceName(), invitation.inviterEmail(), invitation.role(),
                formatExpiry(invitation.expiresAt()), invitation.acceptanceUrl());
    }

    String html(InvitationEmail invitation) {
        return """
                <p>You\u2019ve been invited to join <strong>%s</strong> on AssetSphere.</p>
                <p>Invited by: %s<br>Role: %s<br>Invitation expires: %s</p>
                <p><a href="%s">Review and respond to this secure, single-use invitation</a></p>
                <p>If you were not expecting this invitation, you can ignore this email.</p>
                """.formatted(escape(invitation.workspaceName()), escape(invitation.inviterEmail()),
                escape(invitation.role()), escape(formatExpiry(invitation.expiresAt())),
                HtmlUtils.htmlEscape(invitation.acceptanceUrl(), "UTF-8"));
    }

    String formatExpiry(Instant expiresAt) {
        return expiryFormatter.format(expiresAt);
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value, "UTF-8");
    }
}
