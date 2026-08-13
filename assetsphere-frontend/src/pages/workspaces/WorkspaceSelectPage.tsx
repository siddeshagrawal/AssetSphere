import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Building2, Plus, ChevronRight, Layers, LogOut } from "lucide-react";
import { useCurrentUser } from "@/features/auth/AuthProvider";
import { useWorkspaces } from "@/features/workspaces/hooks";
import { CreateWorkspaceDialog } from "@/features/workspaces/CreateWorkspaceDialog";
import { PageHeader } from "@/components/shared/PageHeader";
import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorDisplay } from "@/components/shared/ErrorDisplay";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import type { WorkspaceRole } from "@/types/workspace";
import { useLogout } from "@/features/auth/hooks";

const ROLE_LABEL: Record<WorkspaceRole, string> = {
  OWNER: "Owner",
  ADMIN: "Admin",
  MEMBER: "Member",
  VIEWER: "Viewer",
  AUDITOR: "Auditor",
};

const ROLE_CLASS: Record<WorkspaceRole, string> = {
  OWNER: "bg-primary/10 text-primary",
  ADMIN: "bg-amber-500/10 text-amber-700",
  MEMBER: "bg-secondary text-secondary-foreground",
  VIEWER: "bg-muted text-muted-foreground",
  AUDITOR: "bg-muted text-muted-foreground",
};

export function WorkspaceSelectPage() {
  const user = useCurrentUser();
  const navigate = useNavigate();
  const { data: workspaces, isLoading, isError, error, refetch } = useWorkspaces();
  const [createOpen, setCreateOpen] = useState(false);
  const logout = useLogout();

  return (
    <div className="min-h-screen bg-muted/20 px-4 py-10 sm:py-16">
      <div className="mx-auto w-full max-w-5xl">
        <header className="mb-10 flex flex-col gap-5 border-b border-border pb-6 sm:flex-row sm:items-center sm:justify-between"><Link to="/" className="flex items-center gap-3 rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"><span className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary"><Layers className="h-5 w-5 text-primary-foreground" /></span><div><p className="text-lg font-semibold tracking-tight">AssetSphere</p><p className="text-xs text-muted-foreground">Enterprise knowledge workspaces</p></div></Link><div className="flex items-center justify-between gap-4 rounded-lg border border-border bg-card px-3 py-2 sm:justify-start"><div><p className="text-sm font-medium">{user.displayName}</p><p className="text-xs text-muted-foreground">{user.email}</p></div><Button variant="ghost" size="icon" disabled={logout.isPending} onClick={() => logout.mutate()} aria-label="Sign out"><LogOut className="h-4 w-4" /></Button></div></header>
        <PageHeader
          title={`Welcome back, ${user.displayName.split(" ")[0]}`}
          description="Select a workspace to continue or create a new one."
          actions={
            <Button size="sm" onClick={() => setCreateOpen(true)}>
              <Plus className="h-4 w-4" aria-hidden="true" />
              New workspace
            </Button>
          }
        />

        {/* Loading */}
        {isLoading && (
          <div className="space-y-2" aria-label="Loading workspaces" aria-busy="true">
            {[1, 2, 3].map((i) => (
              <div
                key={i}
                className="flex items-center gap-4 rounded-lg border border-border p-4"
              >
                <Skeleton className="h-10 w-10 rounded-md" />
                <div className="flex-1 space-y-2">
                  <Skeleton className="h-4 w-40" />
                  <Skeleton className="h-3 w-24" />
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Error */}
        {isError && (
          <ErrorDisplay error={error} onRetry={() => refetch()} />
        )}

        {/* Empty */}
        {!isLoading && !isError && workspaces?.length === 0 && (
          <EmptyState
            icon={Building2}
            title="No workspaces yet"
            description="Create your first workspace to start managing your organization's assets."
            action={
              <Button onClick={() => setCreateOpen(true)}>
                <Plus className="h-4 w-4" />
                Create workspace
              </Button>
            }
          />
        )}

        {/* Workspace list */}
        {!isLoading && !isError && workspaces && workspaces.length > 0 && (
          <ul className="grid gap-4 md:grid-cols-2" role="list" aria-label="Your workspaces">
            {workspaces.map((ws) => (
              <li key={ws.id}>
                <button
                  type="button"
                  onClick={() => navigate(`/workspaces/${ws.id}`)}
                  className={cn(
                    "group flex min-h-28 w-full items-center gap-4 rounded-xl border border-border bg-card px-5 py-5 shadow-sm",
                    "hover:-translate-y-0.5 hover:border-primary/30 hover:shadow-md transition-all text-left",
                    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                  )}
                  aria-label={`Open workspace ${ws.name}`}
                >
                  {/* Workspace icon */}
                  <div
                    className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-primary/10 text-lg font-bold text-primary uppercase"
                    aria-hidden="true"
                  >
                    {ws.name.charAt(0)}
                  </div>

                  {/* Info */}
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-foreground truncate">
                      {ws.name}
                    </p>
                    <p className="text-xs text-muted-foreground truncate">/{ws.slug} · Secure workspace</p>
                  </div>

                  {/* Role badge */}
                  <span
                    className={cn(
                      "shrink-0 text-[10px] font-semibold uppercase tracking-wider px-2 py-0.5 rounded",
                      ROLE_CLASS[ws.role]
                    )}
                  >
                    {ROLE_LABEL[ws.role]}
                  </span>

                  <span className="flex items-center gap-1 text-xs font-medium text-muted-foreground group-hover:text-primary">Open <ChevronRight className="h-4 w-4" aria-hidden="true" /></span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>

      <CreateWorkspaceDialog open={createOpen} onOpenChange={setCreateOpen} />
    </div>
  );
}
