package com.assetsphere.modules.workspace.api;

import java.time.Instant;

public interface WorkspaceInvitationEmailSender {
    void send(InvitationEmail invitation);

    record InvitationEmail(
            String recipientEmail,
            String inviterEmail,
            String workspaceName,
            String role,
            Instant expiresAt,
            String acceptanceUrl
    ) { }
}
