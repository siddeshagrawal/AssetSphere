/**
 * Workspace API functions.
 *
 * All functions return the unwrapped data payload.
 * They throw ApiError on failure.
 */

import type { AxiosResponse } from "axios";
import { apiClient, unwrap } from "@/lib/api-client";
import type { ApiResponse } from "@/types/api";
import type {
  WorkspaceResponse,
  WorkspaceSummary,
  CreateWorkspaceRequest,
  UpdateWorkspaceRequest,
  WorkspaceMemberResponse,
  WorkspaceInvitationResponse,
  InviteWorkspaceMemberRequest,
  ChangeWorkspaceRoleRequest,
  AcceptWorkspaceInvitationRequest,
  WorkspaceInvitationDetailsResponse,
} from "@/types/workspace";

const BASE = "/api/v1/workspaces";

/**
 * GET /api/v1/workspaces
 *
 * Returns a list of workspace summaries the current user belongs to.
 */
export async function listWorkspaces(): Promise<WorkspaceSummary[]> {
  const response: AxiosResponse<ApiResponse<WorkspaceSummary[]>> =
    await apiClient.get(BASE);
  return unwrap(response);
}

/**
 * POST /api/v1/workspaces
 *
 * Creates a new workspace owned by the current user.
 * Backend: name/slug max 160 chars, description max 2000 chars.
 */
export async function createWorkspace(
  request: CreateWorkspaceRequest
): Promise<WorkspaceResponse> {
  const response: AxiosResponse<ApiResponse<WorkspaceResponse>> =
    await apiClient.post(BASE, request);
  return unwrap(response);
}

/**
 * GET /api/v1/workspaces/{workspaceId}
 *
 * Returns full workspace detail.
 * Returns 404 for both missing and unauthorized access (backend intentional).
 */
export async function getWorkspace(
  workspaceId: string
): Promise<WorkspaceResponse> {
  const response: AxiosResponse<ApiResponse<WorkspaceResponse>> =
    await apiClient.get(`${BASE}/${workspaceId}`);
  return unwrap(response);
}

/**
 * PATCH /api/v1/workspaces/{workspaceId}
 *
 * Partial update for name/description. Typed here for future use.
 */
export async function updateWorkspace(
  workspaceId: string,
  request: UpdateWorkspaceRequest
): Promise<WorkspaceResponse> {
  const response: AxiosResponse<ApiResponse<WorkspaceResponse>> =
    await apiClient.patch(`${BASE}/${workspaceId}`, request);
  return unwrap(response);
}

export async function listWorkspaceMembers(workspaceId: string): Promise<WorkspaceMemberResponse[]> {
  const response: AxiosResponse<ApiResponse<WorkspaceMemberResponse[]>> =
    await apiClient.get(`${BASE}/${workspaceId}/members`);
  return unwrap(response);
}

export async function inviteWorkspaceMember(
  workspaceId: string,
  request: InviteWorkspaceMemberRequest
): Promise<WorkspaceInvitationResponse> {
  const response: AxiosResponse<ApiResponse<WorkspaceInvitationResponse>> =
    await apiClient.post(`${BASE}/${workspaceId}/invitations`, request);
  return unwrap(response);
}

export async function changeWorkspaceMemberRole(
  workspaceId: string,
  memberId: string,
  request: ChangeWorkspaceRoleRequest
): Promise<WorkspaceMemberResponse> {
  const response: AxiosResponse<ApiResponse<WorkspaceMemberResponse>> =
    await apiClient.patch(`${BASE}/${workspaceId}/members/${memberId}/role`, request);
  return unwrap(response);
}

export async function removeWorkspaceMember(workspaceId: string, memberId: string): Promise<void> {
  await apiClient.delete(`${BASE}/${workspaceId}/members/${memberId}`);
}

export async function acceptWorkspaceInvitation(
  request: AcceptWorkspaceInvitationRequest
): Promise<WorkspaceMemberResponse> {
  const response: AxiosResponse<ApiResponse<WorkspaceMemberResponse>> =
    await apiClient.post(`${BASE}/invitations/accept`, request);
  return unwrap(response);
}

export async function validateWorkspaceInvitation(token: string): Promise<WorkspaceInvitationDetailsResponse> {
  const response: AxiosResponse<ApiResponse<WorkspaceInvitationDetailsResponse>> =
    await apiClient.get(`${BASE}/invitations/validate`, { params: { token } });
  return unwrap(response);
}

export async function declineWorkspaceInvitation(request: AcceptWorkspaceInvitationRequest): Promise<void> {
  await apiClient.post(`${BASE}/invitations/decline`, request);
}
