package com.assetsphere.modules.workspace.api.dto.response;

import java.util.UUID;

public record WorkspaceResponse(
        UUID id,
        String name,
        String slug,
        String description,
        String status
) {
}
