package com.assetsphere.modules.workspace.api;

import java.util.List;
import java.util.UUID;

/**
 * Public workspace provisioning and summary API used by Authentication.
 */
public interface WorkspaceFacade {

    WorkspaceSummary createPersonalWorkspace(UUID userId, String displayName);

    List<WorkspaceSummary> findWorkspacesForUser(UUID userId);
}
