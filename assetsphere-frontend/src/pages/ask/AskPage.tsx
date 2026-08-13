import { useState, type FormEvent } from "react";
import { ArrowRight, BookOpen, Loader2, MessageSquareText, Sparkles } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageHeader } from "@/components/shared/PageHeader";
import { Button } from "@/components/ui/button";
import { useAskWorkspace } from "@/features/ask/hooks";
import { ApiError } from "@/types/api";
import { useAiModels } from "@/features/quiz-hooks";

export function AskPage() {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const [question, setQuestion] = useState("");
  const models = useAiModels(workspaceId ?? "");
  const [modelId, setModelId] = useState("");
  const ask = useAskWorkspace(workspaceId ?? "");
  if (!workspaceId) return null;

  function submit(event: FormEvent) {
    event.preventDefault();
    const normalized = question.trim();
    if (normalized) ask.mutate({ question: normalized, modelId: modelId || undefined });
  }

  const apiError = ask.error instanceof ApiError ? ask.error : null;
  const errorMessage = apiError?.status === 429
    ? apiError.code === "QUOTA_EXCEEDED"
      ? apiError.message
      : `Question limit reached.${apiError.retryAfterSeconds ? ` Try again in ${apiError.retryAfterSeconds} seconds.` : " Please wait and try again."}`
    : apiError?.status === 503
      ? "Workspace AI is temporarily unavailable. Please try again shortly."
      : ask.error
        ? "The question could not be answered. Please try again."
        : null;

  return (
    <div className="p-6">
      <div className="mx-auto max-w-4xl">
        <PageHeader title="Ask AssetSphere" description="Ask questions grounded only in content from this workspace." />
        <div className="rounded-xl border border-border bg-card shadow-sm">
          <form onSubmit={submit} className="p-4">
            <label htmlFor="workspace-question" className="sr-only">Question</label>
            <textarea id="workspace-question" value={question} onChange={(event) => setQuestion(event.target.value)} maxLength={200} rows={3} disabled={ask.isPending} placeholder="What would you like to know about this workspace?" className="w-full resize-none border-0 bg-transparent text-sm leading-6 outline-none placeholder:text-muted-foreground disabled:opacity-60" />
            <div className="mt-3 flex flex-wrap items-center justify-between gap-3 border-t border-border pt-3"><p className="text-xs text-muted-foreground">Grounded answers include source citations · {question.length}/200</p><div className="flex items-center gap-2"><label htmlFor="ask-model" className="sr-only">AI model</label><select id="ask-model" value={modelId} onChange={(event) => setModelId(event.target.value)} className="h-9 rounded-md border border-input bg-background px-3 text-xs"><option value="">Plan default</option>{models.data?.filter((model) => model.capabilities.includes("ASK")).map((model) => <option key={model.modelId} value={model.modelId}>{model.displayName}</option>)}</select><Button type="submit" disabled={!question.trim() || ask.isPending}>{ask.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4" />}{ask.isPending ? "Answering…" : "Ask"}</Button></div></div>
          </form>
        </div>

        {errorMessage && <div className="mt-4 rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive" role="alert">{errorMessage}</div>}
        {!ask.data && !ask.isPending && !errorMessage && <EmptyState className="mt-8" icon={MessageSquareText} title="Ask across your workspace" description="AssetSphere retrieves relevant documents first, then generates an answer from those sources only." />}
        {ask.isPending && <div className="mt-8 rounded-lg border border-border bg-muted/20 p-6"><div className="flex items-center gap-3"><Loader2 className="h-5 w-5 animate-spin text-primary" /><div><p className="text-sm font-medium">Reviewing workspace sources</p><p className="text-xs text-muted-foreground">Finding relevant evidence and preparing a grounded answer.</p></div></div></div>}

        {ask.data && (
          <div className="mt-8 space-y-5">
            <article className="rounded-lg border border-border bg-card p-6"><div className="mb-4 flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground"><Sparkles className="h-4 w-4 text-primary" /> Grounded answer</div><p className="whitespace-pre-wrap text-sm leading-7 text-foreground">{ask.data.answer}</p></article>
            {ask.data.citations.length > 0 && <section><div className="mb-3 flex items-center gap-2"><BookOpen className="h-4 w-4 text-muted-foreground" /><h2 className="text-sm font-medium">Sources</h2></div><div className="grid gap-2 sm:grid-cols-2">{ask.data.citations.map((citation) => <Link key={citation.sourceId} to={`/workspaces/${workspaceId}/assets/${citation.assetId}`} className="group rounded-lg border border-border bg-card p-4 transition-colors hover:bg-muted/30 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"><div className="flex items-start justify-between gap-3"><div className="min-w-0"><span className="text-[10px] font-semibold uppercase tracking-wider text-primary">{citation.sourceId}</span><h3 className="mt-1 truncate text-sm font-medium">{citation.title || citation.filename || "Workspace asset"}</h3></div><ArrowRight className="h-4 w-4 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-0.5" /></div>{citation.snippet && <p className="mt-2 line-clamp-3 text-xs leading-5 text-muted-foreground">{citation.snippet}</p>}</Link>)}</div></section>}
          </div>
        )}
      </div>
    </div>
  );
}
