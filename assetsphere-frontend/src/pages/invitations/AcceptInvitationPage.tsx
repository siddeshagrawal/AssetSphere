import type { ReactNode } from "react";
import { CalendarClock, Loader2, ShieldCheck, UserCheck, X } from "lucide-react";
import { Link, useLocation, useSearchParams } from "react-router-dom";
import { GoogleAuthButton } from "@/components/auth/GoogleAuthButton";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/features/auth/AuthProvider";
import { useAcceptWorkspaceInvitation, useDeclineWorkspaceInvitation, useInvitation } from "@/features/workspaces/hooks";
import { formatBackendDate } from "@/lib/utils";
import { ApiError } from "@/types/api";

export function AcceptInvitationPage() {
  const [params] = useSearchParams();
  const location = useLocation();
  const token = params.get("token") ?? "";
  const { session } = useAuth();
  const invitation = useInvitation(token);
  const accept = useAcceptWorkspaceInvitation(invitation.data?.workspaceId);
  const decline = useDeclineWorkspaceInvitation();
  const returnTo = `${location.pathname}${location.search}`;
  const error = accept.error instanceof ApiError ? accept.error.message : decline.error instanceof ApiError ? decline.error.message : null;
  const actionable = invitation.data?.status === "PENDING";

  return <main className="flex min-h-screen items-center justify-center bg-muted/20 px-4 py-10"><section className="w-full max-w-lg rounded-2xl border border-border bg-card p-7 shadow-sm">
    <div className="flex items-start gap-4"><span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-primary/10"><UserCheck className="h-5 w-5 text-primary" /></span><div><p className="text-xs font-semibold uppercase tracking-wider text-primary">Workspace invitation</p><h1 className="mt-1 text-xl font-semibold">Join {invitation.data?.workspaceName ?? "AssetSphere"}</h1><p className="mt-1 text-sm text-muted-foreground">Review the invitation before joining.</p></div></div>
    {!token && <p className="mt-6 rounded-md bg-destructive/5 p-3 text-sm text-destructive">This invitation link is missing its token.</p>}
    {invitation.isLoading && <div className="mt-6 flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" />Validating secure invitation…</div>}
    {invitation.isError && <p className="mt-6 rounded-md bg-destructive/5 p-3 text-sm text-destructive" role="alert">This invitation is invalid or unavailable.</p>}
    {invitation.data && <div className="mt-6 divide-y divide-border rounded-lg border border-border bg-background px-4"><Detail label="Workspace" value={invitation.data.workspaceName} /><Detail label="Role" value={invitation.data.role} /><Detail label="Invited account" value={invitation.data.inviteeEmail} /><Detail label="Invited by" value={invitation.data.inviterEmail ?? "Workspace administrator"} /><Detail label="Expires" value={formatBackendDate(invitation.data.expiresAt, "MMM d, yyyy 'at' h:mm a")} icon={<CalendarClock className="h-3.5 w-3.5" />} /></div>}
    {invitation.data && !actionable && <p className="mt-5 rounded-md bg-muted p-3 text-sm text-muted-foreground">This invitation is {invitation.data.status.toLowerCase()} and can no longer be used.</p>}
    {error && <p className="mt-5 text-sm text-destructive" role="alert">{error}</p>}
    {actionable && session.status === "UNAUTHENTICATED" && <div className="mt-6 space-y-3"><p className="flex gap-2 text-sm text-muted-foreground"><ShieldCheck className="mt-0.5 h-4 w-4 shrink-0" />Sign in with the invited email address. AssetSphere will return you here automatically.</p><GoogleAuthButton returnTo={returnTo} /><div className="grid grid-cols-2 gap-3"><Button asChild variant="outline"><Link to={`/login?returnTo=${encodeURIComponent(returnTo)}`}>Sign in</Link></Button><Button asChild><Link to={`/register?returnTo=${encodeURIComponent(returnTo)}`}>Create account</Link></Button></div></div>}
    {actionable && session.status === "AUTHENTICATED" && <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end"><Button variant="outline" disabled={accept.isPending || decline.isPending} onClick={() => decline.mutate({ invitationToken: token })}><X className="h-4 w-4" />Decline</Button><Button disabled={accept.isPending || decline.isPending} onClick={() => accept.mutate({ invitationToken: token })}>{accept.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <UserCheck className="h-4 w-4" />}Accept invitation</Button></div>}
    <p className="mt-5 text-xs text-muted-foreground">Invitation links are single-use and bound to the intended email address.</p>
  </section></main>;
}

function Detail({ label, value, icon }: { label: string; value: string; icon?: ReactNode }) {
  return <div className="flex items-center justify-between gap-4 py-3 text-sm"><span className="text-muted-foreground">{label}</span><span className="flex items-center gap-1.5 text-right font-medium">{icon}{value}</span></div>;
}
