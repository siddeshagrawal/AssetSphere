package com.assetsphere.modules.workspace.api;

/**
 * Roles exposed to other application modules without exposing the Workspace domain enum.
 */
public enum WorkspaceRoleView {
    OWNER,
    ADMIN,
    MEMBER,
    VIEWER,
    AUDITOR
}
