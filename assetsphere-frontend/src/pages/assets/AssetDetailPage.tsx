import { useEffect, useState } from "react";
import { ArrowLeft, Brain, BookOpenCheck, Download, Eye, FileText, GitCompareArrows, History, Info, Loader2, Pencil, RefreshCw, Sparkles, UploadCloud } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { ErrorDisplay } from "@/components/shared/ErrorDisplay";
import { PageHeader } from "@/components/shared/PageHeader";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { AssetStatusBadge } from "@/features/assets/AssetStatusBadge";
import { UploadAssetVersionDialog } from "@/features/assets/UploadAssetVersionDialog";
import { EditAssetDialog } from "@/features/assets/EditAssetDialog";
import { CompareVersionsDialog } from "@/features/assets/CompareVersionsDialog";
import { useAsset, useAssetIntelligence, useAssetVersions, useDownloadAssetVersion, useGenerateAssetIntelligence } from "@/features/assets/hooks";
import type { AssetIntelligenceResponse } from "@/types/asset";
import { formatBackendDate, formatBytes } from "@/lib/utils";
import { friendlyFileType } from "@/lib/file-formats";
import { useAiModels } from "@/features/quiz-hooks";

export function AssetDetailPage() {
  const { workspaceId, assetId } = useParams<{ workspaceId: string; assetId: string }>();
  const [versionUploadOpen, setVersionUploadOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [compareOpen, setCompareOpen] = useState(false);
  const [selectedVersionNumber, setSelectedVersionNumber] = useState<number | null>(null);
  const [intelligenceModelId, setIntelligenceModelId] = useState("");
  const aiModels = useAiModels(workspaceId ?? "");
  const asset = useAsset(workspaceId, assetId);
  const versions = useAssetVersions(workspaceId, assetId);
  const download = useDownloadAssetVersion(workspaceId ?? "");
  const selectedNumber = selectedVersionNumber ?? asset.data?.versionNumber;
  const selectedVersion = versions.data?.find((version) => version.versionNumber === selectedNumber);
  const selectedStatus = selectedVersion?.processingStatus ?? (selectedNumber === asset.data?.versionNumber ? asset.data?.processingStatus : undefined);
  const canHaveIntelligence = selectedStatus === "READY" || selectedStatus === "PARTIALLY_PROCESSED";
  const intelligence = useAssetIntelligence(workspaceId, assetId, selectedNumber, canHaveIntelligence);
  const generateIntelligence = useGenerateAssetIntelligence(workspaceId ?? "", assetId ?? "", selectedNumber ?? 0);

  useEffect(() => {
    if (selectedVersionNumber === null && asset.data) setSelectedVersionNumber(asset.data.versionNumber);
  }, [asset.data, selectedVersionNumber]);

  if (!workspaceId || !assetId) return null;
  if (asset.isLoading) return <DetailSkeleton />;
  if (asset.isError) return <div className="p-6 max-w-3xl"><ErrorDisplay error={asset.error} onRetry={() => asset.refetch()} /></div>;
  if (!asset.data) return null;

  return (
    <div className="p-6">
      <div className="mx-auto max-w-4xl">
        <Link to={`/workspaces/${workspaceId}/assets`} className="mb-5 inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground">
          <ArrowLeft className="h-4 w-4" /> Back to assets
        </Link>
        <PageHeader
          title={asset.data.displayName || asset.data.originalFilename}
          description={asset.data.originalFilename}
          actions={<>
            <Button variant="outline" onClick={() => setEditOpen(true)}><Pencil className="h-4 w-4" /> Edit</Button>
            {selectedNumber && canHaveIntelligence && <Button asChild variant="outline"><Link to={`/workspaces/${workspaceId}/insights?assetId=${assetId}&versionNumber=${selectedNumber}`}><BookOpenCheck className="h-4 w-4" /> Generate insight</Link></Button>}
            <Button variant="outline" disabled={download.isPending || !selectedNumber} onClick={() => download.mutate({ assetId, versionNumber: selectedNumber!, fallbackFilename: selectedVersion?.originalFilename ?? asset.data.originalFilename })}>
              <Download className="h-4 w-4" /> Download Version {selectedNumber}
            </Button>
            <Button onClick={() => setVersionUploadOpen(true)}><UploadCloud className="h-4 w-4" /> Upload new version</Button>
          </>}
        />

        <div className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-primary/20 bg-primary/5 px-4 py-3">
          <div><p className="text-sm font-medium">Viewing Version {selectedNumber}</p><p className="mt-0.5 text-xs text-muted-foreground">Latest is Version {asset.data.versionNumber}. Intelligence and downloads follow the version you are viewing.</p></div>
          {selectedNumber !== asset.data.versionNumber && <Button variant="outline" size="sm" onClick={() => setSelectedVersionNumber(asset.data.versionNumber)}>Return to latest</Button>}
        </div>

        <section className="rounded-lg border border-border bg-card">
          <div className="flex items-center gap-3 border-b border-border px-5 py-4">
            <div className="flex h-9 w-9 items-center justify-center rounded-md bg-muted"><FileText className="h-4 w-4 text-muted-foreground" /></div>
            <div className="flex-1"><h2 className="text-sm font-medium">Asset details</h2><p className="text-xs text-muted-foreground">Current metadata and processing state</p></div>
            {selectedStatus && <AssetStatusBadge status={selectedStatus} />}
          </div>
          <dl className="grid grid-cols-1 gap-x-8 gap-y-5 px-5 py-5 sm:grid-cols-2">
            <Detail label="Display name" value={asset.data.displayName || "—"} />
            <Detail label="Filename" value={selectedVersion?.originalFilename ?? asset.data.originalFilename} />
            <Detail label="Type" value={`${friendlyFileType(selectedVersion?.originalFilename ?? asset.data.originalFilename, selectedVersion?.mimeType ?? asset.data.mimeType, asset.data.assetType)} · ${selectedVersion?.mimeType ?? asset.data.mimeType}`} />
            <Detail label="Version" value={`Version ${selectedNumber}${selectedNumber === asset.data.versionNumber ? " · Latest" : ""}`} />
            <Detail label="Lifecycle status" value={asset.data.lifecycleStatus} />
            <Detail label="File size" value={formatBytes(selectedVersion?.fileSize ?? asset.data.fileSize)} />
            <Detail label="Uploaded" value={formatBackendDate(selectedVersion?.createdAt ?? asset.data.createdAt, "MMM d, yyyy 'at' h:mm a")} />
            <Detail label="Description" value={asset.data.description || "No description"} />
          </dl>
        </section>

        <section className="mt-6 rounded-lg border border-border bg-card">
          <div className="flex items-center gap-3 border-b border-border px-5 py-4">
            <div className="flex h-9 w-9 items-center justify-center rounded-md bg-muted"><History className="h-4 w-4 text-muted-foreground" /></div>
            <div className="flex-1"><h2 className="text-sm font-medium">Version history</h2><p className="text-xs text-muted-foreground">Every uploaded revision, newest first</p></div>
            <div className="flex items-center gap-2">
              {versions.data && versions.data.length >= 2 && <Button variant="outline" size="sm" onClick={() => setCompareOpen(true)}><GitCompareArrows className="h-3.5 w-3.5" /> Compare versions</Button>}
              {versions.data && <span className="text-xs text-muted-foreground">{versions.data.length} version{versions.data.length === 1 ? "" : "s"}</span>}
            </div>
          </div>
          {versions.isLoading && <div className="space-y-2 p-5">{Array.from({ length: 2 }).map((_, index) => <Skeleton key={index} className="h-16 w-full" />)}</div>}
          {versions.isError && <div className="p-5"><ErrorDisplay error={versions.error} onRetry={() => versions.refetch()} title="Unable to load version history" /></div>}
          {versions.data && (
            <ul className="divide-y divide-border" aria-label="Asset version history">
              {versions.data.map((version) => {
                const current = version.versionNumber === asset.data.versionNumber;
                const selected = version.versionNumber === selectedNumber;
                const downloading = download.isPending && download.variables?.versionNumber === version.versionNumber;
                return (
                  <li key={version.assetVersionId} className={`flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-center ${selected ? "bg-primary/[0.03]" : ""}`}>
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <p className="text-sm font-medium">Version {version.versionNumber}</p>
                        {current && <span className="rounded bg-primary/10 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wider text-primary">Latest</span>}
                        {selected && <span className="rounded bg-muted px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wider text-foreground">Viewing</span>}
                        <AssetStatusBadge status={version.processingStatus} />
                      </div>
                      <p className="mt-1 truncate text-xs text-muted-foreground">{version.originalFilename} · {version.mimeType} · {formatBytes(version.fileSize)}</p>
                      <p className="mt-1 text-[11px] text-muted-foreground">Uploaded {formatBackendDate(version.createdAt, "MMM d, yyyy 'at' h:mm a")}</p>
                    </div>
                    <div className="flex gap-2"><Button variant={selected ? "secondary" : "outline"} size="sm" onClick={() => setSelectedVersionNumber(version.versionNumber)}><Eye className="h-3.5 w-3.5" />{selected ? "Viewing" : "View"}</Button><Button variant="outline" size="sm" disabled={download.isPending} onClick={() => download.mutate({ assetId, versionNumber: version.versionNumber, fallbackFilename: version.originalFilename })}><Download className="h-3.5 w-3.5" /> {downloading ? "Downloading…" : "Download"}</Button></div>
                  </li>
                );
              })}
            </ul>
          )}
        </section>

        <section className="mt-6 rounded-lg border border-border bg-card">
          <div className="flex items-center gap-3 border-b border-border px-5 py-4">
            <div className="flex h-9 w-9 items-center justify-center rounded-md bg-muted"><Brain className="h-4 w-4 text-muted-foreground" /></div>
            <div className="flex-1"><h2 className="text-sm font-medium">AI intelligence</h2><p className="text-xs text-muted-foreground">Grounded analysis generated from this asset</p></div><label htmlFor="intelligence-model" className="sr-only">AI model</label><select id="intelligence-model" value={intelligenceModelId} onChange={(event) => setIntelligenceModelId(event.target.value)} className="h-9 max-w-48 rounded-md border border-input bg-background px-3 text-xs"><option value="">Plan default</option>{aiModels.data?.filter((model) => model.capabilities.includes("INTELLIGENCE")).map((model) => <option key={model.modelId} value={model.modelId}>{model.displayName}</option>)}</select>
          </div>
          <div className="p-5">
            {selectedStatus === "FAILED" && <IntelligenceMessage title="Processing failed" description="This version could not be processed, so intelligence is unavailable." />}
            {(selectedStatus === "UPLOADED" || selectedStatus === "QUEUED" || selectedStatus === "PROCESSING") && <IntelligenceMessage title="Intelligence pending" description="Intelligence will become available after this version finishes processing." />}
            {canHaveIntelligence && intelligence.isLoading && <div className="space-y-3"><Skeleton className="h-4 w-32" /><Skeleton className="h-20 w-full" /><Skeleton className="h-4 w-2/3" /></div>}
            {canHaveIntelligence && intelligence.isError && <ErrorDisplay error={intelligence.error} onRetry={() => intelligence.refetch()} title="Unable to load intelligence" />}
            {canHaveIntelligence && intelligence.data?.assetVersionId === (selectedVersion?.assetVersionId ?? asset.data.assetVersionId) && (
              <IntelligenceContent
                intelligence={intelligence.data}
                generating={generateIntelligence.isPending}
                onGenerate={() => generateIntelligence.mutate(intelligenceModelId || undefined)}
              />
            )}
          </div>
        </section>
      </div>
      <UploadAssetVersionDialog workspaceId={workspaceId} assetId={assetId} currentVersion={asset.data.versionNumber} open={versionUploadOpen} onOpenChange={setVersionUploadOpen} onUploaded={setSelectedVersionNumber} />
      <EditAssetDialog workspaceId={workspaceId} assetId={assetId} displayName={asset.data.displayName} description={asset.data.description} originalFilename={asset.data.originalFilename} open={editOpen} onOpenChange={setEditOpen} />
      {versions.data && <CompareVersionsDialog workspaceId={workspaceId} assetId={assetId} versions={versions.data} open={compareOpen} onOpenChange={setCompareOpen} />}
    </div>
  );
}

function IntelligenceContent({ intelligence, generating, onGenerate }: { intelligence: AssetIntelligenceResponse; generating: boolean; onGenerate: () => void }) {
  if (intelligence.status === "NOT_GENERATED") {
    return <div className="rounded-lg border border-dashed border-border bg-muted/20 p-6 text-center"><div className="mx-auto flex h-11 w-11 items-center justify-center rounded-full bg-primary/10"><Sparkles className="h-5 w-5 text-primary" /></div><h3 className="mt-4 text-sm font-semibold">AI insights haven&apos;t been generated yet.</h3><p className="mx-auto mt-2 max-w-md text-sm leading-6 text-muted-foreground">Generate a grounded summary, key points, and tags for this version when you need them.</p><div className="mt-3 flex justify-center gap-2 text-xs text-muted-foreground"><span>Summary</span><span>·</span><span>Key points</span><span>·</span><span>Tags</span></div><Button className="mt-5" onClick={onGenerate} disabled={generating}>{generating ? <Loader2 className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4" />}{generating ? "Starting…" : "Generate AI Insights"}</Button></div>;
  }
  if (intelligence.status === "FAILED") {
    return <div className="rounded-md bg-muted/40 p-5"><div className="flex gap-3"><Info className="mt-0.5 h-4 w-4 shrink-0 text-destructive" /><div className="flex-1"><p className="text-sm font-medium">Intelligence generation failed</p><p className="mt-1 text-sm text-muted-foreground">The asset remains available. Retry when you&apos;re ready.</p><Button className="mt-4" variant="outline" size="sm" onClick={onGenerate} disabled={generating}>{generating ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <RefreshCw className="h-3.5 w-3.5" />}{generating ? "Retrying…" : "Retry AI Insights"}</Button></div></div></div>;
  }
  if (intelligence.status === "PENDING" || intelligence.status === "PROCESSING") return <div className="flex gap-3 rounded-md bg-primary/5 p-4"><Loader2 className="mt-0.5 h-4 w-4 shrink-0 animate-spin text-primary" /><div><p className="text-sm font-medium">Generating AI insights</p><p className="mt-0.5 text-sm text-muted-foreground">This version is being analyzed. You can leave this page while processing continues.</p></div></div>;
  if (intelligence.status === "NOT_APPLICABLE") return <IntelligenceMessage title="Intelligence not applicable" description="AI analysis is not available for this asset type." />;
  if (intelligence.status === "DISABLED") return <IntelligenceMessage title="Intelligence disabled" description="AI analysis is not enabled for this workspace." />;
  return (
    <div className="space-y-6">
      <div><h3 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">Summary</h3><p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-foreground">{intelligence.summary || "No summary was generated."}</p></div>
      {intelligence.keyPoints.length > 0 && <div><h3 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">Key points</h3><ul className="mt-2 space-y-2">{intelligence.keyPoints.map((point, index) => <li key={index} className="flex gap-2 text-sm leading-6"><span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-primary" />{point}</li>)}</ul></div>}
      {intelligence.tags.length > 0 && <div><h3 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">Tags</h3><div className="mt-2 flex flex-wrap gap-2">{intelligence.tags.map((tag) => <span key={tag} className="rounded-md bg-muted px-2.5 py-1 text-xs text-muted-foreground">{tag}</span>)}</div></div>}
    </div>
  );
}

function IntelligenceMessage({ title, description }: { title: string; description: string }) {
  return <div className="flex gap-3 rounded-md bg-muted/40 p-4"><Info className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" /><div><p className="text-sm font-medium">{title}</p><p className="mt-0.5 text-sm text-muted-foreground">{description}</p></div></div>;
}

function Detail({ label, value }: { label: string; value: string }) {
  return <div><dt className="text-xs font-medium text-muted-foreground">{label}</dt><dd className="mt-1 break-words text-sm text-foreground">{value}</dd></div>;
}

function DetailSkeleton() {
  return <div className="p-6"><div className="mx-auto max-w-4xl space-y-4"><Skeleton className="h-4 w-28" /><Skeleton className="h-7 w-64" /><Skeleton className="h-56 w-full rounded-lg" /><Skeleton className="h-64 w-full rounded-lg" /></div></div>;
}
