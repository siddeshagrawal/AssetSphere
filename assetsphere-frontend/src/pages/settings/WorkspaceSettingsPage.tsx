import { useEffect, useState, type FormEvent } from "react";
import { Bot, CreditCard, Loader2, Save, ShieldCheck, Users } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { ErrorDisplay } from "@/components/shared/ErrorDisplay";
import { PageHeader } from "@/components/shared/PageHeader";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { useAuth } from "@/features/auth/AuthProvider";
import { useWorkspaceBilling } from "@/features/billing/hooks";
import { useAiModels } from "@/features/quiz-hooks";
import { useUpdateWorkspace, useWorkspace } from "@/features/workspaces/hooks";

export function WorkspaceSettingsPage() {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { session } = useAuth();
  const workspace = useWorkspace(workspaceId);
  const billing = useWorkspaceBilling(workspaceId);
  const models = useAiModels(workspaceId ?? "");
  const update = useUpdateWorkspace(workspaceId ?? "");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");

  useEffect(() => { if (workspace.data) { setName(workspace.data.name); setDescription(workspace.data.description ?? ""); } }, [workspace.data]);
  if (!workspaceId || session.status !== "AUTHENTICATED") return null;
  const role = session.workspaces.find((item) => item.id === workspaceId)?.role;
  const canEdit = role === "OWNER" || role === "ADMIN";
  if (workspace.isLoading) return <div className="p-6"><Skeleton className="h-72 max-w-4xl rounded-lg" /></div>;
  if (workspace.isError) return <div className="max-w-4xl p-6"><ErrorDisplay error={workspace.error} onRetry={() => workspace.refetch()} /></div>;

  function submit(event: FormEvent) { event.preventDefault(); if (name.trim()) update.mutate({ name: name.trim(), description: description.trim() }); }
  const availableModels = models.data?.filter((model) => model.enabled) ?? [];

  return <div className="p-6"><div className="max-w-4xl"><PageHeader title="Workspace settings" description="Manage identity, access, billing, and enabled knowledge capabilities." />
    <div className="space-y-5">
      <section className="rounded-xl border border-border bg-card p-5"><SectionTitle title="General" description="Workspace identity visible to members." /><form onSubmit={submit} className="mt-5 space-y-4"><div className="space-y-2"><Label htmlFor="workspace-name">Name</Label><Input id="workspace-name" value={name} maxLength={160} disabled={!canEdit || update.isPending} onChange={(event) => setName(event.target.value)} /></div><div className="space-y-2"><Label htmlFor="workspace-description">Description</Label><textarea id="workspace-description" value={description} maxLength={2_000} rows={4} disabled={!canEdit || update.isPending} onChange={(event) => setDescription(event.target.value)} className="w-full resize-y rounded-md border border-input bg-background px-3 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50" /></div><div className="flex items-center justify-between gap-4"><p className="text-xs text-muted-foreground">Read-only identifier: /{workspace.data?.slug}</p>{canEdit && <Button type="submit" disabled={!name.trim() || update.isPending}>{update.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}Save changes</Button>}</div></form></section>
      <div className="grid gap-5 md:grid-cols-2"><SettingsLink icon={Users} title="Members & access" description="Manage roles and create secure single-use invitations." to={`/workspaces/${workspaceId}/members`} action="Manage members" /><SettingsLink icon={CreditCard} title="Billing" description={`Current plan: ${billing.data?.plan ?? "Loading…"}`} to={`/workspaces/${workspaceId}/billing`} action="View plan and usage" /></div>
      <section className="rounded-xl border border-border bg-card p-5"><div className="flex gap-3"><span className="rounded-lg bg-primary/10 p-2"><Bot className="h-4 w-4 text-primary" /></span><SectionTitle title="AI & knowledge" description="Capabilities are enforced by your plan and backend configuration." /></div><div className="mt-5 grid gap-3 sm:grid-cols-3"><Capability label="Available AI models" value={models.isLoading ? "Loading…" : String(availableModels.length)} /><Capability label="Image OCR" value={billing.data?.entitlements.ocrEnabled ? "Enabled" : "Plan gated"} /><Capability label="Video transcription" value={billing.data?.entitlements.videoTranscriptionEnabled ? "Enabled" : "Plan gated"} /></div>{availableModels.length > 0 && <p className="mt-4 text-xs text-muted-foreground">Available: {availableModels.map((model) => model.displayName).join(", ")}. Model choice is validated server-side for each operation.</p>}</section>
      <section className="rounded-xl border border-border bg-card p-5"><div className="flex gap-3"><ShieldCheck className="mt-0.5 h-4 w-4 text-muted-foreground" /><SectionTitle title="Security" description="Workspace deletion is not exposed because the backend does not currently support it. No non-functional danger actions are shown." /></div></section>
    </div>
  </div></div>;
}

function SectionTitle({ title, description }: { title: string; description: string }) { return <div><h2 className="text-sm font-semibold">{title}</h2><p className="mt-1 text-xs text-muted-foreground">{description}</p></div>; }
function Capability({ label, value }: { label: string; value: string }) { return <div className="rounded-lg border border-border bg-background p-3"><p className="text-xs text-muted-foreground">{label}</p><p className="mt-1 text-sm font-medium">{value}</p></div>; }
function SettingsLink({ icon: Icon, title, description, to, action }: { icon: typeof Users; title: string; description: string; to: string; action: string }) { return <section className="rounded-xl border border-border bg-card p-5"><div className="flex gap-3"><span className="rounded-lg bg-primary/10 p-2"><Icon className="h-4 w-4 text-primary" /></span><SectionTitle title={title} description={description} /></div><Button asChild className="mt-5" variant="outline"><Link to={to}>{action}</Link></Button></section>; }
