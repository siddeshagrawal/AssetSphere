import { useMemo, useState } from "react";
import { Download, FileText, UploadCloud, ChevronLeft, ChevronRight } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { PageHeader } from "@/components/shared/PageHeader";
import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorDisplay } from "@/components/shared/ErrorDisplay";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Input } from "@/components/ui/input";
import { AssetStatusBadge } from "@/features/assets/AssetStatusBadge";
import { UploadAssetDialog } from "@/features/assets/UploadAssetDialog";
import { useAssets, useDownloadAssetVersion } from "@/features/assets/hooks";
import { backendDate, formatBackendDate, formatBytes } from "@/lib/utils";
import { friendlyFileType } from "@/lib/file-formats";

export function AssetsPage() {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const [page, setPage] = useState(0);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [filter, setFilter] = useState("");
  const [status, setStatus] = useState("ALL");
  const [type, setType] = useState("ALL");
  const [sort, setSort] = useState("NEWEST");
  const assets = useAssets(workspaceId, page);
  const download = useDownloadAssetVersion(workspaceId ?? "");
  const visibleAssets = useMemo(() => {
    const normalized = filter.trim().toLowerCase();
    return [...(assets.data?.content ?? [])].filter((asset) => (!normalized || asset.displayName.toLowerCase().includes(normalized) || asset.originalFilename.toLowerCase().includes(normalized)) && (status === "ALL" || asset.processingStatus === status) && (type === "ALL" || asset.assetType === type)).sort((left, right) => sort === "NAME" ? left.displayName.localeCompare(right.displayName) : backendDate(right.createdAt).getTime() - backendDate(left.createdAt).getTime());
  }, [assets.data?.content, filter, sort, status, type]);

  if (!workspaceId) return null;

  return (
    <div className="p-6">
      <div className="mx-auto max-w-5xl">
        <PageHeader
          title="Assets"
          description="Documents, images, and videos available in this workspace."
          actions={
            <Button onClick={() => setUploadOpen(true)}>
              <UploadCloud className="h-4 w-4" /> Upload asset
            </Button>
          }
        />
        <div className="mb-4 grid gap-2 rounded-lg border border-border bg-card p-3 sm:grid-cols-[1fr_auto_auto_auto]"><Input value={filter} onChange={(event) => setFilter(event.target.value)} placeholder="Filter by filename or display name" aria-label="Filter assets" /><select value={status} onChange={(event) => setStatus(event.target.value)} className="h-9 rounded-md border border-input bg-background px-3 text-sm" aria-label="Processing status"><option value="ALL">All statuses</option>{["UPLOADED","QUEUED","PROCESSING","READY","PARTIALLY_PROCESSED","FAILED"].map((value) => <option key={value}>{value}</option>)}</select><select value={type} onChange={(event) => setType(event.target.value)} className="h-9 rounded-md border border-input bg-background px-3 text-sm" aria-label="File type"><option value="ALL">All types</option>{["PDF","DOCX","IMAGE","OTHER"].map((value) => <option key={value}>{value}</option>)}</select><select value={sort} onChange={(event) => setSort(event.target.value)} className="h-9 rounded-md border border-input bg-background px-3 text-sm" aria-label="Sort assets"><option value="NEWEST">Newest</option><option value="NAME">Name</option></select></div>

        {assets.isLoading && (
          <div className="space-y-2" aria-label="Loading assets">
            {Array.from({ length: 5 }).map((_, index) => (
              <Skeleton key={index} className="h-16 w-full rounded-lg" />
            ))}
          </div>
        )}

        {assets.isError && <ErrorDisplay error={assets.error} onRetry={() => assets.refetch()} />}

        {!assets.isLoading && !assets.isError && assets.data?.content.length === 0 && (
          <EmptyState
            icon={FileText}
            title="No assets yet"
            description="Upload the first document, image, or video for this workspace."
            action={<Button onClick={() => setUploadOpen(true)}><UploadCloud className="h-4 w-4" /> Upload asset</Button>}
          />
        )}

        {assets.data && assets.data.content.length > 0 && (
          <>
            <div className="overflow-hidden rounded-lg border border-border bg-card">
              {visibleAssets.length === 0 && <p className="p-6 text-center text-sm text-muted-foreground">No assets match the current filters.</p>}
              <ul className="divide-y divide-border" aria-label="Workspace assets">
                {visibleAssets.map((asset) => (
                  <li key={asset.assetId} className="flex items-center pr-3 transition-colors hover:bg-muted/40">
                    <Link
                      to={`/workspaces/${workspaceId}/assets/${asset.assetId}`}
                      className="flex min-w-0 flex-1 items-center gap-4 px-4 py-3.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring"
                    >
                      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-muted">
                        <FileText className="h-4 w-4 text-muted-foreground" />
                      </div>
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium text-foreground">{asset.displayName || asset.originalFilename}</p>
                        <p className="mt-0.5 truncate text-xs text-muted-foreground">
                          {asset.originalFilename} · {friendlyFileType(asset.originalFilename, asset.mimeType, asset.assetType)} · {formatBytes(asset.fileSize)}
                        </p>
                      </div>
                      <div className="hidden text-right sm:block">
                        <p className="text-xs text-muted-foreground">Version {asset.versionNumber}</p>
                        <p className="mt-0.5 text-[11px] text-muted-foreground">{formatBackendDate(asset.createdAt)}</p>
                      </div>
                      <AssetStatusBadge status={asset.processingStatus} />
                    </Link>
                    <Button
                      variant="ghost"
                      size="icon"
                      disabled={download.isPending}
                      aria-label={`Download ${asset.originalFilename}, Version ${asset.versionNumber}`}
                      title={`Download Version ${asset.versionNumber}`}
                      onClick={() => download.mutate({ assetId: asset.assetId, versionNumber: asset.versionNumber, fallbackFilename: asset.originalFilename })}
                    >
                      <Download className="h-4 w-4" />
                    </Button>
                  </li>
                ))}
              </ul>
            </div>
            {assets.data.totalPages > 1 && (
              <div className="mt-4 flex items-center justify-between">
                <p className="text-xs text-muted-foreground">
                  Page {assets.data.page + 1} of {assets.data.totalPages} · {assets.data.totalElements} assets
                </p>
                <div className="flex gap-2">
                  <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>
                    <ChevronLeft className="h-4 w-4" /> Previous
                  </Button>
                  <Button variant="outline" size="sm" disabled={page + 1 >= assets.data.totalPages} onClick={() => setPage((value) => value + 1)}>
                    Next <ChevronRight className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            )}
          </>
        )}
      </div>
      <UploadAssetDialog workspaceId={workspaceId} open={uploadOpen} onOpenChange={setUploadOpen} />
    </div>
  );
}
