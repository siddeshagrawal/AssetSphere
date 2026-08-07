package com.assetsphere.modules.workspace.api.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateWorkspaceRequest(
        @Size(min = 1, max = 160)
        String name,

        @Size(max = 2000)
        String description
) {
}
