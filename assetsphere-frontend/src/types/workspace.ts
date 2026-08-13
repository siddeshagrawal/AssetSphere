/**
 * Workspace types — mirrors of backend DTOs.
 *
 * Sources:
 *   WorkspaceResponse.java
 *   WorkspaceSummary.java
 *   WorkspaceMemberResponse.java
 *   WorkspaceInvitationResponse.java
 *   WorkspaceRoleView.java (enum)
 *   CreateWorkspaceRequest.java
 *   UpdateWorkspaceRequest.java
 *   InviteWorkspaceMemberRequest.java
 *   AcceptWorkspaceInvitationRequest.java
 *   ChangeWorkspaceRoleRequest.java
 */

// ── Enums ────────────────────────────────────────────────────────────────────

/** Mirrors WorkspaceRoleView.java */
export type WorkspaceRole = "OWNER" | "ADMIN" | "MEMBER" | "VIEWER" | "AUDITOR";

// ── Responses ────────────────────────────────────────────────────────────────

/**
 * Compact view returned inside CurrentUserResponse and list endpoints.
 * Source: WorkspaceSummary.java
 */
export interface WorkspaceSummary {
  id: string;
  name: string;
  slug: string;
  role: WorkspaceRole;
}

/**
 * Full workspace detail.
 * Source: WorkspaceResponse.java
 */
export interface WorkspaceResponse {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  status: string;
}

/**
 * Source: WorkspaceMemberResponse.java
 *
 * NOTE: Backend gap — userId is a UUID but no displayName/email is returned.
 * The members list can only show userId until the backend enriches this DTO.
 */
export interface WorkspaceMemberResponse {
  id: string;
  userId: string;
  displayName: string | null;
  email: string | null;
  role: WorkspaceRole;
  status: string;
  joinedAt: string | number;
}

/**
 * Source: WorkspaceInvitationResponse.java
 */
export interface WorkspaceInvitationResponse {
  id: string;
  inviteeEmail: string;
  role: WorkspaceRole;
  expiresAt: string | number;
  invitationToken: string;
  invitationUrl: string;
  emailDeliveryStatus: "SENT" | "DISABLED" | "FAILED";
}

export interface WorkspaceInvitationDetailsResponse {
  invitationId: string;
  workspaceId: string;
  workspaceName: string;
  inviterEmail: string | null;
  inviteeEmail: string;
  role: WorkspaceRole;
  status: "PENDING" | "ACCEPTED" | "DECLINED" | "EXPIRED" | "REVOKED";
  expiresAt: string | number;
}

// ── Requests ─────────────────────────────────────────────────────────────────

/** Source: CreateWorkspaceRequest.java — name/slug max 160, description max 2000 */
export interface CreateWorkspaceRequest {
  name: string;
  slug: string;
  description?: string;
}

/** Source: UpdateWorkspaceRequest.java */
export interface UpdateWorkspaceRequest {
  name?: string;
  description?: string;
}

/** Source: InviteWorkspaceMemberRequest.java */
export interface InviteWorkspaceMemberRequest {
  email: string;
  role: WorkspaceRole;
}

/** Source: AcceptWorkspaceInvitationRequest.java */
export interface AcceptWorkspaceInvitationRequest {
  invitationToken: string;
}

/** Source: ChangeWorkspaceRoleRequest.java */
export interface ChangeWorkspaceRoleRequest {
  role: WorkspaceRole;
}
