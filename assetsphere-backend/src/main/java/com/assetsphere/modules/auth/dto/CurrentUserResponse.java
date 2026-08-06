package com.assetsphere.modules.auth.dto;

import java.util.List;

import com.assetsphere.modules.workspace.api.WorkspaceSummary;

public record CurrentUserResponse(UserResponse user, List<WorkspaceSummary> workspaces) {
}
