package com.assetsphere.modules.workspace.api;

import java.util.UUID;

public record WorkspaceSummary(UUID id, String name, String slug, String role) {
}
