package com.assetsphere.modules.workspace.api;

import java.util.Set;
import java.util.UUID;

/**
 * Public authorization contract for modules that operate within a workspace.
 */
public interface WorkspaceAccessFacade {

    void requireActiveMembership(UUID workspaceId, UUID userId);

    void requireRole(UUID workspaceId, UUID userId, Set<WorkspaceRoleView> roles);
}
