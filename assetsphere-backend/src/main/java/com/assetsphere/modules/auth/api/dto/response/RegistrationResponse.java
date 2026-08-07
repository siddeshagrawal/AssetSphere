package com.assetsphere.modules.auth.api.dto.response;

import com.assetsphere.modules.workspace.api.WorkspaceSummary;

public record RegistrationResponse(
        UserResponse user,
        WorkspaceSummary defaultWorkspace
) {
}
