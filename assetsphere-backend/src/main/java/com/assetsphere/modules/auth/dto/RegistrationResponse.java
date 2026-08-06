package com.assetsphere.modules.auth.dto;
import com.assetsphere.modules.workspace.api.WorkspaceSummary;
public record RegistrationResponse(UserResponse user, WorkspaceSummary defaultWorkspace) { }
