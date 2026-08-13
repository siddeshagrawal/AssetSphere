/**
 * Workspace TanStack Query hooks.
 */

import { useEffect, useRef } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useNavigate } from "react-router-dom";
import * as workspaceApi from "@/api/workspace.api";
import { useAuth } from "@/features/auth/AuthProvider";
import type { AcceptWorkspaceInvitationRequest, ChangeWorkspaceRoleRequest, CreateWorkspaceRequest, InviteWorkspaceMemberRequest, UpdateWorkspaceRequest } from "@/types/workspace";
import { ApiError } from "@/types/api";

// ── Query keys (centralised to prevent typos) ────────────────────────────────

export const workspaceKeys = {
  all: ["workspaces"] as const,
  lists: () => [...workspaceKeys.all, "list"] as const,
  detail: (id: string) => [...workspaceKeys.all, "detail", id] as const,
  members: (id: string) => [...workspaceKeys.all, "members", id] as const,
  invitation: (token: string) => [...workspaceKeys.all, "invitation", token] as const,
};

// ── useWorkspaces ────────────────────────────────────────────────────────────

/**
 * Returns the current user's workspace list.
 * Only enabled when the session is AUTHENTICATED.
 */
export function useWorkspaces() {
  const { session } = useAuth();
  const enabled = session.status === "AUTHENTICATED";

  return useQuery({
    queryKey: workspaceKeys.lists(),
    queryFn: () => workspaceApi.listWorkspaces(),
    enabled,
  });
}

export function useWorkspaceMembers(workspaceId: string | undefined) {
  return useQuery({
    queryKey: workspaceKeys.members(workspaceId ?? ""),
    queryFn: () => workspaceApi.listWorkspaceMembers(workspaceId!),
    enabled: !!workspaceId,
  });
}

export function useInviteWorkspaceMember(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: InviteWorkspaceMemberRequest) => workspaceApi.inviteWorkspaceMember(workspaceId, request),
    onSuccess: (invitation) => toast.success(`Invitation created for ${invitation.inviteeEmail}.`),
    onError: (error: Error) => {
      if (!(error instanceof ApiError)) toast.error("Invitation could not be sent.");
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: workspaceKeys.members(workspaceId) }),
  });
}

export function useChangeWorkspaceMemberRole(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ memberId, request }: { memberId: string; request: ChangeWorkspaceRoleRequest }) =>
      workspaceApi.changeWorkspaceMemberRole(workspaceId, memberId, request),
    onSuccess: () => {
      toast.success("Member role updated.");
      queryClient.invalidateQueries({ queryKey: workspaceKeys.members(workspaceId) });
    },
    onError: () => toast.error("Member role could not be updated."),
  });
}

export function useRemoveWorkspaceMember(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (memberId: string) => workspaceApi.removeWorkspaceMember(workspaceId, memberId),
    onSuccess: () => {
      toast.success("Member removed.");
      queryClient.invalidateQueries({ queryKey: workspaceKeys.members(workspaceId) });
    },
    onError: (error: Error) => toast.error(error instanceof ApiError ? error.message : "Member could not be removed."),
  });
}

export function useUpdateWorkspace(workspaceId: string) {
  const queryClient = useQueryClient();
  const { refreshSession } = useAuth();
  return useMutation({
    mutationFn: (request: UpdateWorkspaceRequest) => workspaceApi.updateWorkspace(workspaceId, request),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: workspaceKeys.detail(workspaceId) });
      await queryClient.invalidateQueries({ queryKey: workspaceKeys.lists() });
      await refreshSession();
      toast.success("Workspace updated.");
    },
    onError: () => toast.error("Workspace could not be updated."),
  });
}

export function useInvitation(token: string) {
  return useQuery({
    queryKey: workspaceKeys.invitation(token),
    queryFn: () => workspaceApi.validateWorkspaceInvitation(token),
    enabled: Boolean(token),
    retry: false,
  });
}

export function useAcceptWorkspaceInvitation(workspaceId?: string) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { refreshSession } = useAuth();
  return useMutation({
    mutationFn: (request: AcceptWorkspaceInvitationRequest) => workspaceApi.acceptWorkspaceInvitation(request),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: workspaceKeys.all });
      await refreshSession();
      toast.success("Workspace invitation accepted.");
      navigate(workspaceId ? `/workspaces/${workspaceId}` : "/workspaces");
    },
  });
}

export function useDeclineWorkspaceInvitation() {
  const navigate = useNavigate();
  return useMutation({
    mutationFn: (request: AcceptWorkspaceInvitationRequest) => workspaceApi.declineWorkspaceInvitation(request),
    onSuccess: () => {
      toast.success("Workspace invitation declined.");
      navigate("/workspaces");
    },
  });
}

// ── useWorkspace ─────────────────────────────────────────────────────────────

/**
 * Returns full detail for a single workspace.
 * Returns 404 for both missing and unauthorized access (backend behaviour).
 */
export function useWorkspace(workspaceId: string | undefined) {
  const { session, refreshSession } = useAuth();
  const refreshedDeniedWorkspace = useRef<string | null>(null);
  const enabled = session.status === "AUTHENTICATED" && !!workspaceId;

  const query = useQuery({
    queryKey: workspaceKeys.detail(workspaceId ?? ""),
    queryFn: () => workspaceApi.getWorkspace(workspaceId!),
    enabled,
    retry: (failureCount, error) => {
      // Never retry 404 — backend conceals unauthorized workspaces as 404
      const status = (error as { status?: number }).status;
      if (status === 404) return false;
      return failureCount < 1;
    },
  });

  useEffect(() => {
    const status = (query.error as { status?: number } | null)?.status;
    if (query.isError && status === 404 && workspaceId && refreshedDeniedWorkspace.current !== workspaceId) {
      refreshedDeniedWorkspace.current = workspaceId;
      void refreshSession();
    }
  }, [query.error, query.isError, refreshSession, workspaceId]);

  return query;
}

// ── useCreateWorkspace ───────────────────────────────────────────────────────

export function useCreateWorkspace() {
  const qc = useQueryClient();
  const navigate = useNavigate();
  const { refreshSession } = useAuth();

  return useMutation({
    mutationFn: (request: CreateWorkspaceRequest) =>
      workspaceApi.createWorkspace(request),
    onSuccess: async (created) => {
      // Invalidate the workspace list so the selector/page refreshes
      await qc.invalidateQueries({ queryKey: workspaceKeys.lists() });
      // Refresh /auth/me so AuthProvider's workspace list is current
      await refreshSession();
      toast.success(`Workspace "${created.name}" created.`);
      navigate(`/workspaces/${created.id}`);
    },
    onError: (error: Error) => {
      if (!(error instanceof ApiError)) {
        toast.error("Failed to create workspace. Please try again.");
      }
    },
  });
}
