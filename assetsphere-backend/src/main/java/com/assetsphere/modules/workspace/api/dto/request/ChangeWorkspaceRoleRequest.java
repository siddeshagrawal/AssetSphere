package com.assetsphere.modules.workspace.api.dto.request;

import com.assetsphere.modules.workspace.api.WorkspaceRoleView;
import jakarta.validation.constraints.NotNull;

public record ChangeWorkspaceRoleRequest(
        @NotNull
        WorkspaceRoleView role
) {
}
