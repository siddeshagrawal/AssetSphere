package com.assetsphere.modules.workspace.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceInvitationDetailsResponse(
        UUID invitationId,
        UUID workspaceId,
        String workspaceName,
        String inviterEmail,
        String inviteeEmail,
        String role,
        String status,
        Instant expiresAt
) { }
