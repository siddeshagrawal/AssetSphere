import { useState } from "react";
import { ArrowRight, FileText, MessageSquareText, Search, UploadCloud } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { ErrorDisplay } from "@/components/shared/ErrorDisplay";
import { PageHeader } from "@/components/shared/PageHeader";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useWorkspaceActivity } from "@/features/activity/hooks";
import { AssetStatusBadge } from "@/features/assets/AssetStatusBadge";
import { UploadAssetDialog } from "@/features/assets/UploadAssetDialog";
import { useAssets } from "@/features/assets/hooks";
import { useWorkspaceBilling } from "@/features/billing/hooks";
import { useWorkspace } from "@/features/workspaces/hooks";
import { formatBackendDate, formatBytes } from "@/lib/utils";

const ACTIONS: Record<string, string> = { ASSET_UPLOADED: "Asset uploaded", INTELLIGENCE_GENERATED: "AI intelligence generated", INTELLIGENCE_FAILED: "AI intelligence failed", WORKSPACE_UPDATED: "Workspace updated", WORKSPACE_MEMBER_INVITED: "Member invited", WORKSPACE_INVITATION_ACCEPTED: "Invitation accepted", WORKSPACE_MEMBER_ROLE_CHANGED: "Member role changed", WORKSPACE_MEMBER_REMOVED: "Member removed" };

export function WorkspaceOverviewPage() {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const [uploadOpen, setUploadOpen] = useState(false);
  const workspace = useWorkspace(workspaceId);
  const assets = useAssets(workspaceId, 0);
  const billing = useWorkspaceBilling(workspaceId);
  const activity = useWorkspaceActivity(workspaceId, 8);
  if (!workspaceId) return null;
  if (workspace.isLoading) return <div className="p-6"><Skeleton className="h-8 w-64" /><Skeleton className="mt-6 h-48 w-full max-w-5xl" /></div>;
  if (workspace.isError) return <div className="p-6"><ErrorDisplay title="Workspace unavailable" error={workspace.error} onRetry={() => workspace.refetch()} /><div className="mt-4 text-center"><Button asChild variant="outline"><Link to="/workspaces">Back to workspaces</Link></Button></div></div>;

  return <div className="p-6"><div className="mx-auto max-w-6xl">
    <PageHeader title={workspace.data?.name ?? "Workspace"} description={workspace.data?.description ?? "Your secure knowledge workspace."} actions={<Button onClick={() => setUploadOpen(true)}><UploadCloud className="h-4 w-4" />Upload asset</Button>} />
    <div className="grid gap-3 sm:grid-cols-3"><Shortcut to={`/workspaces/${workspaceId}/assets`} icon={FileText} title="Browse assets" text="Manage files and versions" /><Shortcut to={`/workspaces/${workspaceId}/search`} icon={Search} title="Search knowledge" text="Lexical, semantic and hybrid" /><Shortcut to={`/workspaces/${workspaceId}/ask`} icon={MessageSquareText} title="Ask AssetSphere" text="Grounded answers with citations" /></div>
    <div className="mt-6 grid gap-6 lg:grid-cols-[1.35fr_.65fr]">
      <section className="rounded-xl border border-border bg-card"><div className="flex items-center justify-between border-b border-border px-5 py-4"><h2 className="font-semibold">Recent assets</h2><Link className="text-xs font-medium text-primary hover:underline" to={`/workspaces/${workspaceId}/assets`}>View all</Link></div>{assets.isLoading ? <div className="space-y-2 p-5"><Skeleton className="h-14" /><Skeleton className="h-14" /></div> : assets.isError ? <p className="p-5 text-sm text-destructive">Recent assets could not be loaded.</p> : assets.data?.content.length ? <ul className="divide-y divide-border">{assets.data.content.slice(0, 6).map((asset) => <li key={asset.assetId}><Link to={`/workspaces/${workspaceId}/assets/${asset.assetId}`} className="flex items-center gap-3 px-5 py-3 hover:bg-muted/30"><FileText className="h-4 w-4 text-muted-foreground" /><div className="min-w-0 flex-1"><p className="truncate text-sm font-medium">{asset.displayName}</p><p className="text-xs text-muted-foreground">Version {asset.versionNumber} · {formatBytes(asset.fileSize)} · {formatBackendDate(asset.createdAt)}</p></div><AssetStatusBadge status={asset.processingStatus} /></Link></li>)}</ul> : <p className="p-6 text-sm text-muted-foreground">No assets yet. Upload the first file to begin.</p>}</section>
      <div className="space-y-6"><section className="rounded-xl border border-border bg-card p-5"><div className="flex items-center justify-between"><div><p className="text-xs font-semibold uppercase tracking-wider text-primary">Current plan</p><h2 className="mt-1 text-2xl font-semibold">{billing.data?.plan ?? "—"}</h2></div><Link className="text-xs font-medium text-primary hover:underline" to={`/workspaces/${workspaceId}/billing`}>Manage</Link></div>{billing.isError ? <p className="mt-4 text-xs text-destructive">Billing details could not be loaded.</p> : billing.data && <div className="mt-4 space-y-2 text-xs text-muted-foreground"><p>{billing.data.usage.assets} / {billing.data.entitlements.maxAssets} assets</p><p>{billing.data.usage.aiInsights} / {billing.data.entitlements.monthlyAiInsights} AI insights</p><p>{billing.data.usage.askRequests} / {billing.data.entitlements.monthlyAskRequests} Ask requests</p></div>}</section>
      <section className="rounded-xl border border-border bg-card"><h2 className="border-b border-border px-5 py-4 font-semibold">Recent activity</h2>{activity.isLoading ? <div className="space-y-2 p-5"><Skeleton className="h-10" /><Skeleton className="h-10" /></div> : activity.isError ? <p className="p-5 text-sm text-destructive">Activity could not be loaded.</p> : activity.data?.content.length ? <ul className="divide-y divide-border">{activity.data.content.map((item) => <li key={item.id} className="px-5 py-3"><p className="text-sm font-medium">{ACTIONS[item.action] ?? item.action.replace(/_/g, " ").toLowerCase()}</p><p className="mt-1 text-xs text-muted-foreground">{formatBackendDate(item.occurredAt)}</p></li>)}</ul> : <p className="p-5 text-sm text-muted-foreground">No workspace activity yet.</p>}</section></div>
    </div><UploadAssetDialog workspaceId={workspaceId} open={uploadOpen} onOpenChange={setUploadOpen} />
  </div></div>;
}

function Shortcut({ to, icon: Icon, title, text }: { to: string; icon: typeof FileText; title: string; text: string }) {
  return <Link to={to} className="group rounded-xl border border-border bg-card p-4 transition-colors hover:bg-muted/30"><div className="flex items-center justify-between"><Icon className="h-5 w-5 text-primary" /><ArrowRight className="h-4 w-4 text-muted-foreground transition-transform group-hover:translate-x-0.5" /></div><p className="mt-4 text-sm font-semibold">{title}</p><p className="mt-1 text-xs text-muted-foreground">{text}</p></Link>;
}
