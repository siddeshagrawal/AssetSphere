package com.assetsphere.modules.workspace.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceMemberResponse(
        UUID id,
        UUID userId,
        String displayName,
        String email,
        String role,
        String status,
        Instant joinedAt
) {
}
