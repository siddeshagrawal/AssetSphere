package com.assetsphere.modules.workspace.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptWorkspaceInvitationRequest(
        @NotBlank
        @Size(max = 512)
        String invitationToken
) {
}
