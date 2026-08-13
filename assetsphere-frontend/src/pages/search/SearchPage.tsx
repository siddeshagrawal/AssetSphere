import { useState, type FormEvent } from "react";
import { ChevronLeft, ChevronRight, Download, FileSearch, Search } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorDisplay } from "@/components/shared/ErrorDisplay";
import { PageHeader } from "@/components/shared/PageHeader";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { useWorkspaceSearch } from "@/features/search/hooks";
import { useDownloadAssetVersion } from "@/features/assets/hooks";
import { cn } from "@/lib/utils";
import type { SearchMode } from "@/types/search";

const MODES: { value: SearchMode; label: string; description: string }[] = [
  { value: "HYBRID", label: "Hybrid", description: "Best overall relevance" },
  { value: "SEMANTIC", label: "Semantic", description: "Meaning-based matching" },
  { value: "LEXICAL", label: "Lexical", description: "Exact words and phrases" },
];

function plainSnippet(value: string | null) {
  return value?.replace(/<[^>]+>/g, "").replace(/\s+/g, " ").trim() ?? "";
}

export function SearchPage() {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const [input, setInput] = useState("");
  const [query, setQuery] = useState("");
  const [mode, setMode] = useState<SearchMode>("HYBRID");
  const [page, setPage] = useState(0);
  const results = useWorkspaceSearch(workspaceId, query, mode, page);
  const download = useDownloadAssetVersion(workspaceId ?? "");

  if (!workspaceId) return null;

  function submit(event: FormEvent) {
    event.preventDefault();
    const normalized = input.trim();
    if (!normalized) return;
    setPage(0);
    setQuery(normalized);
  }

  return (
    <div className="p-6">
      <div className="mx-auto max-w-5xl">
        <PageHeader title="Search" description="Find assets by exact text or semantic meaning." />
        <form onSubmit={submit} className="flex gap-2">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input value={input} onChange={(event) => setInput(event.target.value)} maxLength={200} className="pl-9" placeholder="Search workspace content…" aria-label="Search workspace" />
          </div>
          <Button type="submit" disabled={!input.trim() || results.isFetching}>Search</Button>
        </form>

        <div className="mt-3 flex flex-wrap gap-2" aria-label="Search mode">
          {MODES.map((option) => (
            <button
              key={option.value}
              type="button"
              title={option.description}
              onClick={() => { setMode(option.value); setPage(0); if (query) setQuery(input.trim() || query); }}
              className={cn(
                "rounded-md border px-3 py-1.5 text-xs font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
                mode === option.value ? "border-primary bg-primary/5 text-primary" : "border-border text-muted-foreground hover:bg-muted"
              )}
            >
              {option.label}
            </button>
          ))}
        </div>

        {!query && <EmptyState className="mt-8" icon={FileSearch} title="Search your workspace" description="Use Hybrid for balanced keyword and meaning-based results." />}
        {results.isLoading && <div className="mt-8 space-y-3">{Array.from({ length: 4 }).map((_, index) => <Skeleton key={index} className="h-28 w-full rounded-lg" />)}</div>}
        {results.isError && <div className="mt-8"><ErrorDisplay error={results.error} onRetry={() => results.refetch()} title="Search unavailable" /></div>}
        {query && results.data?.content.length === 0 && <EmptyState className="mt-8" icon={FileSearch} title="No matching assets" description={`No ${mode.toLowerCase()} results were found for “${query}”.`} />}

        {results.data && results.data.content.length > 0 && (
          <>
            <div className="mt-8 flex items-center justify-between"><p className="text-xs text-muted-foreground">{results.data.totalElements} result{results.data.totalElements === 1 ? "" : "s"} for “{query}”</p><span className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">{mode}</span></div>
            <ul className="mt-3 space-y-2">
              {results.data.content.map((result) => (
                <li key={`${result.assetId}-${result.assetVersionId}`}>
                  <div className="rounded-lg border border-border bg-card p-4 transition-colors hover:bg-muted/30"><Link to={`/workspaces/${workspaceId}/assets/${result.assetId}`} className="block focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
                    <div className="flex items-start justify-between gap-4"><div className="min-w-0"><h2 className="truncate text-sm font-medium">{result.displayName || result.originalFilename || "Untitled asset"}</h2><p className="mt-0.5 truncate text-xs text-muted-foreground">{result.originalFilename || result.mimeType || "Asset"}</p></div>{result.processingStatus && <span className="rounded bg-muted px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">{result.processingStatus}</span>}</div>
                    {plainSnippet(result.snippet) && <p className="mt-3 line-clamp-3 text-sm leading-6 text-muted-foreground">{plainSnippet(result.snippet)}</p>}
                  </Link><div className="mt-3 flex justify-end"><Button variant="outline" size="sm" disabled={download.isPending} onClick={() => download.mutate({ assetId: result.assetId, versionNumber: result.versionNumber, fallbackFilename: result.originalFilename || result.displayName || "asset" })}><Download className="h-3.5 w-3.5" />Download Version {result.versionNumber}</Button></div></div>
                </li>
              ))}
            </ul>
            {results.data.totalPages > 1 && <div className="mt-4 flex justify-end gap-2"><Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((value) => value - 1)}><ChevronLeft className="h-4 w-4" /> Previous</Button><Button variant="outline" size="sm" disabled={page + 1 >= results.data.totalPages} onClick={() => setPage((value) => value + 1)}>Next <ChevronRight className="h-4 w-4" /></Button></div>}
          </>
        )}
      </div>
    </div>
  );
}
