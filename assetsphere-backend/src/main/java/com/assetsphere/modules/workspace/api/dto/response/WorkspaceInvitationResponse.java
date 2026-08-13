package com.assetsphere.modules.workspace.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceInvitationResponse(
        UUID id,
        String inviteeEmail,
        String role,
        Instant expiresAt,
        String invitationToken,
        String invitationUrl,
        String emailDeliveryStatus
) {
}
