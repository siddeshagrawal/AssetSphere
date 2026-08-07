package com.assetsphere.modules.workspace.api.dto.request;

import com.assetsphere.modules.workspace.api.WorkspaceRoleView;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InviteWorkspaceMemberRequest(
        @NotBlank
        @Email
        @Size(max = 320)
        String email,

        @NotNull
        WorkspaceRoleView role
) {
}
