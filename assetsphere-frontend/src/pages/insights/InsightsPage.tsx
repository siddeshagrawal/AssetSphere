import { useState, type FormEvent } from "react";
import { AlertTriangle, BookOpenCheck, CheckSquare, CircleHelp, FileText, GitCompareArrows, Gavel, Loader2, Sparkles } from "lucide-react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageHeader } from "@/components/shared/PageHeader";
import { Button } from "@/components/ui/button";
import { useGenerateInsight } from "@/features/insights/hooks";
import { useAiModels } from "@/features/quiz-hooks";
import { quotaErrorMessage } from "@/lib/quota-errors";
import { cn } from "@/lib/utils";
import { QuizPage } from "@/pages/QuizPage";
import type { WorkspaceInsightType } from "@/types/insight";

const options = [
  ["EXECUTIVE_BRIEF", "Executive brief", "A concise leadership-ready overview.", FileText],
  ["KEY_DECISIONS", "Key decisions", "Decisions and their evidence.", Gavel],
  ["RISKS_AND_GAPS", "Risks & gaps", "Material issues, severity, and context.", AlertTriangle],
  ["ACTION_ITEMS", "Action items", "Grounded next steps and context.", CheckSquare],
  ["OPEN_QUESTIONS", "Open questions", "What remains unresolved and why.", CircleHelp],
  ["CONTRADICTIONS", "Contradictions", "Conflicting statements across sources.", GitCompareArrows],
  ["KNOWLEDGE_CHECK", "Knowledge Check", "The existing grounded quiz experience.", BookOpenCheck],
] as const;

export function InsightsPage() {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const [params] = useSearchParams();
  const assetId = params.get("assetId") ?? undefined;
  const parsedVersion = Number(params.get("versionNumber"));
  const versionNumber = Number.isInteger(parsedVersion) && parsedVersion > 0 ? parsedVersion : undefined;
  const exactVersion = Boolean(assetId && versionNumber);
  const [type, setType] = useState<WorkspaceInsightType>("EXECUTIVE_BRIEF");
  const [focus, setFocus] = useState("");
  const [modelId, setModelId] = useState("");
  const insight = useGenerateInsight(workspaceId ?? "");
  const models = useAiModels(workspaceId ?? "");
  if (!workspaceId) return null;

  function choose(next: WorkspaceInsightType) { setType(next); insight.reset(); }
  function submit(event: FormEvent) {
    event.preventDefault();
    if (type === "KNOWLEDGE_CHECK") return;
    insight.mutate({ assetId, versionNumber, request: { type, focus: focus.trim() || undefined, modelId: modelId || undefined } });
  }
  const error = insight.error ? quotaErrorMessage(insight.error, "The insight could not be generated. Please try again.") : null;

  return <div className="p-6"><div className="mx-auto max-w-6xl"><PageHeader title="Insights" description={exactVersion ? `Generate grounded intelligence from exact Version ${versionNumber}.` : "Turn authorized workspace evidence into focused, source-backed business intelligence."} actions={exactVersion && assetId ? <Button asChild variant="outline"><Link to={`/workspaces/${workspaceId}/assets/${assetId}`}>Back to asset</Link></Button> : undefined} />
    <section aria-label="Insight types"><div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">{options.map(([value, label, description, Icon]) => <button key={value} type="button" onClick={() => choose(value)} aria-pressed={type === value} className={cn("rounded-xl border bg-card p-4 text-left transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring", type === value ? "border-primary/50 shadow-sm ring-1 ring-primary/20" : "border-border hover:border-primary/30")}><Icon className={cn("h-4 w-4", type === value ? "text-primary" : "text-muted-foreground")} /><p className="mt-3 text-sm font-semibold">{label}</p><p className="mt-1 text-xs leading-5 text-muted-foreground">{description}</p></button>)}</div></section>
    {type === "KNOWLEDGE_CHECK" ? <div className="mt-8 rounded-xl border border-border bg-card p-1"><QuizPage embedded /></div> : <>
      <form onSubmit={submit} className="mt-6 grid gap-4 rounded-xl border border-border bg-card p-5 md:grid-cols-[1fr_220px_auto] md:items-end"><div><label htmlFor="insight-focus" className="text-xs font-medium text-muted-foreground">Optional focus</label><input id="insight-focus" value={focus} maxLength={200} onChange={(event) => { setFocus(event.target.value); insight.reset(); }} placeholder={exactVersion ? "e.g. customer commitments" : "e.g. Q3 operational risks"} className="mt-1.5 h-10 w-full rounded-md border border-input bg-background px-3 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring" /></div><div><label htmlFor="insight-model" className="text-xs font-medium text-muted-foreground">AI model</label><select id="insight-model" value={modelId} onChange={(event) => { setModelId(event.target.value); insight.reset(); }} className="mt-1.5 h-10 w-full rounded-md border border-input bg-background px-3 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"><option value="">Plan default</option>{models.data?.filter((model) => model.capabilities.includes("INTELLIGENCE")).map((model) => <option key={model.modelId} value={model.modelId}>{model.displayName}</option>)}</select></div><Button type="submit" disabled={insight.isPending}>{insight.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4" />}{insight.isPending ? "Generating…" : insight.data ? "Generate again" : "Generate"}</Button></form>
      {error && <div className="mt-4 rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive" role="alert">{error}</div>}
      {!insight.data && !insight.isPending && !error && <EmptyState className="mt-8" icon={Sparkles} title="Generate on demand" description="Select an insight type, optionally add focus, then generate. No provider call occurs until you click Generate." />}
      {insight.isPending && <div className="mt-8 flex items-center gap-3 rounded-lg border border-border bg-muted/20 p-6"><Loader2 className="h-5 w-5 animate-spin text-primary" /><div><p className="text-sm font-medium">Generating grounded insight</p><p className="text-xs text-muted-foreground">Reviewing bounded authorized evidence and validating source references.</p></div></div>}
      {insight.data && !insight.isPending && <section className="mt-8 space-y-5"><div className="rounded-xl border border-border bg-card p-6"><p className="text-xs font-semibold uppercase tracking-wider text-primary">Summary</p><p className="mt-3 text-sm leading-7 text-foreground">{insight.data.summary}</p></div>{insight.data.items.length > 0 && <div className="grid gap-4 md:grid-cols-2">{insight.data.items.map((item, index) => <article key={`${index}-${item.title}`} className="rounded-xl border border-border bg-card p-5">{item.severity && <span className="rounded-full bg-amber-500/10 px-2 py-1 text-[10px] font-semibold uppercase tracking-wider text-amber-700">{item.severity}</span>}<h2 className="mt-2 text-sm font-semibold leading-6">{item.title}</h2>{item.secondary && <p className="mt-2 rounded-md bg-muted/40 p-3 text-sm">{item.secondary}</p>}{item.detail && <p className="mt-2 text-sm leading-6 text-muted-foreground">{item.detail}</p>}{item.sourceIds.length > 0 && <p className="mt-3 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">Sources: {item.sourceIds.join(", ")}</p>}</article>)}</div>}{insight.data.citations.length > 0 && <div className="rounded-xl border border-border bg-card p-5"><h2 className="text-sm font-semibold">Trusted sources</h2><ul className="mt-3 space-y-3">{insight.data.citations.map((citation) => <li key={citation.sourceId} className="rounded-lg bg-muted/30 p-3"><div className="flex items-center justify-between gap-3"><span className="text-xs font-semibold text-primary">{citation.sourceId}</span><Link className="text-xs font-medium hover:text-primary" to={`/workspaces/${workspaceId}/assets/${citation.assetId}`}>{citation.title || citation.filename}</Link></div><p className="mt-2 line-clamp-3 text-xs leading-5 text-muted-foreground">{citation.snippet}</p></li>)}</ul></div>}</section>}
    </>}
  </div></div>;
}
