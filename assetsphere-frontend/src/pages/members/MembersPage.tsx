import { useState } from "react";
import { MoreHorizontal, Trash2, UserPlus, Users } from "lucide-react";
import { useParams } from "react-router-dom";
import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorDisplay } from "@/components/shared/ErrorDisplay";
import { PageHeader } from "@/components/shared/PageHeader";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import { Skeleton } from "@/components/ui/skeleton";
import { useAuth } from "@/features/auth/AuthProvider";
import { InviteMemberDialog } from "@/features/workspaces/InviteMemberDialog";
import { useChangeWorkspaceMemberRole, useRemoveWorkspaceMember, useWorkspaceMembers } from "@/features/workspaces/hooks";
import { formatBackendDate } from "@/lib/utils";
import type { WorkspaceMemberResponse, WorkspaceRole } from "@/types/workspace";

const MANAGEABLE_ROLES: WorkspaceRole[] = ["ADMIN", "MEMBER", "VIEWER", "AUDITOR"];

export function MembersPage() {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { session } = useAuth();
  const [inviteOpen, setInviteOpen] = useState(false);
  const [selectedMember, setSelectedMember] = useState<WorkspaceMemberResponse | null>(null);
  const members = useWorkspaceMembers(workspaceId);
  const changeRole = useChangeWorkspaceMemberRole(workspaceId ?? "");
  const removeMember = useRemoveWorkspaceMember(workspaceId ?? "");
  if (!workspaceId || session.status !== "AUTHENTICATED") return null;

  const currentRole = session.workspaces.find((workspace) => workspace.id === workspaceId)?.role;
  const canManage = currentRole === "OWNER" || currentRole === "ADMIN";

  async function confirmRemoval() {
    if (!selectedMember || removeMember.isPending) return;
    try {
      await removeMember.mutateAsync(selectedMember.id);
      setSelectedMember(null);
    } catch {
      // The mutation displays its typed backend message.
    }
  }

  return <div className="p-6"><div className="mx-auto max-w-5xl">
    <PageHeader title="Members" description="People with access to this workspace." actions={canManage ? <Button onClick={() => setInviteOpen(true)}><UserPlus className="h-4 w-4" />Invite member</Button> : undefined} />
    {members.isLoading && <div className="space-y-2">{Array.from({ length: 4 }).map((_, index) => <Skeleton key={index} className="h-16 w-full rounded-lg" />)}</div>}
    {members.isError && <ErrorDisplay error={members.error} onRetry={() => members.refetch()} />}
    {members.data?.length === 0 && <EmptyState icon={Users} title="No workspace members" description="Invite the first person to collaborate in this workspace." action={canManage ? <Button onClick={() => setInviteOpen(true)}><UserPlus className="h-4 w-4" />Invite member</Button> : undefined} />}
    {members.data && members.data.length > 0 && <div className="overflow-hidden rounded-lg border border-border bg-card"><ul className="divide-y divide-border">{members.data.map((member) => {
      const isCurrentUser = member.userId === session.user.id;
      const displayName = member.displayName ?? (isCurrentUser ? session.user.displayName : "Workspace member");
      const canRemove = canManage && !isCurrentUser && member.role !== "OWNER" && !(currentRole === "ADMIN" && member.role === "ADMIN");
      return <li key={member.id} className="flex flex-col items-stretch gap-3 px-4 py-3.5 sm:flex-row sm:items-center sm:gap-4"><div className="flex min-w-0 items-center gap-4"><div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-muted text-xs font-semibold text-muted-foreground">{displayName.slice(0, 2).toUpperCase()}</div><div className="min-w-0 flex-1"><p className="truncate text-sm font-medium">{displayName}{isCurrentUser ? " (you)" : ""}</p><p className="truncate text-xs text-muted-foreground">{member.email ?? (isCurrentUser ? session.user.email : "")}</p><p className="mt-0.5 truncate text-xs text-muted-foreground">Joined {formatBackendDate(member.joinedAt)} · {member.status}</p></div></div>{canManage && member.role !== "OWNER" ? <div className="flex flex-wrap items-center gap-2 sm:ml-auto sm:shrink-0"><select aria-label={`Role for ${displayName}`} value={member.role} disabled={changeRole.isPending || isCurrentUser} onChange={(event) => changeRole.mutate({ memberId: member.id, request: { role: event.target.value as WorkspaceRole } })} className="h-11 rounded-md border border-input bg-background px-2 text-xs focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring sm:h-8">{MANAGEABLE_ROLES.map((role) => <option key={role} value={role}>{role.charAt(0) + role.slice(1).toLowerCase()}</option>)}</select>{canRemove && <DropdownMenu><DropdownMenuTrigger asChild><Button variant="ghost" size="icon" aria-label={`Actions for ${displayName}`}><MoreHorizontal className="h-4 w-4" /></Button></DropdownMenuTrigger><DropdownMenuContent align="end"><DropdownMenuItem className="text-destructive focus:text-destructive" onSelect={() => setSelectedMember(member)}><Trash2 className="h-4 w-4" />Remove member</DropdownMenuItem></DropdownMenuContent></DropdownMenu>}</div> : <span className="self-start rounded bg-muted px-2 py-1 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground sm:self-auto">{member.role}</span>}</li>;
    })}</ul></div>}
  </div><InviteMemberDialog workspaceId={workspaceId} open={inviteOpen} onOpenChange={setInviteOpen} /><Dialog open={selectedMember !== null} onOpenChange={(open) => { if (!open && !removeMember.isPending) setSelectedMember(null); }}><DialogContent><DialogHeader><DialogTitle>Remove workspace member?</DialogTitle><DialogDescription>This immediately revokes access to this workspace and its assets.</DialogDescription></DialogHeader>{selectedMember && <div className="rounded-lg border border-border bg-muted/30 p-4"><p className="font-medium">{selectedMember.displayName ?? "Workspace member"}</p><p className="text-sm text-muted-foreground">{selectedMember.email ?? "Identity unavailable"}</p></div>}<DialogFooter><Button variant="outline" disabled={removeMember.isPending} onClick={() => setSelectedMember(null)}>Cancel</Button><Button variant="destructive" disabled={removeMember.isPending} onClick={confirmRemoval}>{removeMember.isPending ? "Removing…" : "Remove member"}</Button></DialogFooter></DialogContent></Dialog></div>;
}
