package com.assetsphere.modules.workspace.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWorkspaceRequest(
        @NotBlank
        @Size(max = 160)
        String name,

        @NotBlank
        @Size(max = 160)
        String slug,

        @Size(max = 2000)
        String description
) {
}
