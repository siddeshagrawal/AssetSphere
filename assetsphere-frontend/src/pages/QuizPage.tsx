import { useEffect, useState, type FormEvent } from "react";
import { BookOpenCheck, CheckCircle2, Loader2, RefreshCw, Sparkles } from "lucide-react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageHeader } from "@/components/shared/PageHeader";
import { Button } from "@/components/ui/button";
import { useAiModels, useGenerateQuiz } from "@/features/quiz-hooks";
import { quotaErrorMessage } from "@/lib/quota-errors";
import type { QuizDifficulty } from "@/types/quiz";

export function QuizPage({ embedded = false }: { embedded?: boolean }) {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const [searchParams] = useSearchParams();
  const assetId = searchParams.get("assetId") ?? undefined;
  const parsedVersion = Number(searchParams.get("versionNumber"));
  const versionNumber = assetId && Number.isInteger(parsedVersion) && parsedVersion > 0 ? parsedVersion : undefined;
  const [difficulty, setDifficulty] = useState<QuizDifficulty>("MEDIUM");
  const [questionCount, setQuestionCount] = useState(5);
  const [topic, setTopic] = useState("");
  const models = useAiModels(workspaceId ?? "");
  const [modelId, setModelId] = useState(() => workspaceId ? localStorage.getItem(`assetsphere:ai-model:${workspaceId}`) ?? "" : "");
  const [revealed, setRevealed] = useState<Set<number>>(new Set());
  const quiz = useGenerateQuiz(workspaceId ?? "");

  useEffect(() => setRevealed(new Set()), [quiz.data]);
  useEffect(() => { if (workspaceId && modelId) localStorage.setItem(`assetsphere:ai-model:${workspaceId}`, modelId); }, [workspaceId, modelId]);
  if (!workspaceId) return null;

  function submit(event: FormEvent) {
    event.preventDefault();
    quiz.mutate({
      assetId: scopedToAsset ? assetId : undefined,
      versionNumber: scopedToAsset ? versionNumber : undefined,
      request: { difficulty, questionCount, topic: scopedToAsset ? undefined : topic.trim() || undefined, modelId: modelId || undefined },
    });
  }

  function toggleAnswer(index: number) {
    setRevealed((current) => {
      const next = new Set(current);
      next.has(index) ? next.delete(index) : next.add(index);
      return next;
    });
  }

  const scopedToAsset = Boolean(assetId && versionNumber);
  const error = quiz.error ? quotaErrorMessage(quiz.error, "The quiz could not be generated. Please try again.") : null;

  return (
    <div className={embedded ? "p-5" : "p-6"}>
      <div className="mx-auto max-w-5xl">
        {!embedded && <PageHeader
          title="Insights"
          description={scopedToAsset ? `Create a Knowledge Check from exact Version ${versionNumber}.` : "Generate a grounded Knowledge Check from authorized workspace evidence."}
          actions={scopedToAsset && assetId ? <Button asChild variant="outline"><Link to={`/workspaces/${workspaceId}/assets/${assetId}`}>Back to asset</Link></Button> : undefined}
        />}

        <form onSubmit={submit} className="grid gap-4 rounded-xl border border-border bg-card p-5 shadow-sm lg:grid-cols-[1fr_170px_170px_130px_auto] lg:items-end">
          <div>
            <label htmlFor="quiz-topic" className="text-xs font-medium text-muted-foreground">{scopedToAsset ? "Source" : "Topic or focus"}</label>
            {scopedToAsset ? <p className="mt-2 text-sm font-medium">Selected asset · Version {versionNumber}</p> : <input id="quiz-topic" value={topic} onChange={(event) => setTopic(event.target.value)} maxLength={200} placeholder="Optional: security controls" className="mt-1.5 h-10 w-full rounded-md border border-input bg-background px-3 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring" />}
          </div>
          <div><label htmlFor="quiz-model" className="text-xs font-medium text-muted-foreground">AI model</label><select id="quiz-model" value={modelId} onChange={(event) => setModelId(event.target.value)} className="mt-1.5 h-10 w-full rounded-md border border-input bg-background px-3 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"><option value="">Plan default</option>{models.data?.filter((model) => model.capabilities.includes("QUIZ")).map((model) => <option key={model.modelId} value={model.modelId}>{model.displayName}</option>)}</select></div>
          <div>
            <label htmlFor="quiz-difficulty" className="text-xs font-medium text-muted-foreground">Difficulty</label>
            <select id="quiz-difficulty" value={difficulty} onChange={(event) => setDifficulty(event.target.value as QuizDifficulty)} className="mt-1.5 h-10 w-full rounded-md border border-input bg-background px-3 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring">
              <option value="EASY">Easy</option><option value="MEDIUM">Medium</option><option value="HARD">Hard</option>
            </select>
          </div>
          <div>
            <label htmlFor="quiz-count" className="text-xs font-medium text-muted-foreground">Questions</label>
            <select id="quiz-count" value={questionCount} onChange={(event) => setQuestionCount(Number(event.target.value))} className="mt-1.5 h-10 w-full rounded-md border border-input bg-background px-3 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring">
              {[3, 5, 7, 10, 15].map((count) => <option key={count} value={count}>{count}</option>)}
            </select>
          </div>
          <Button type="submit" disabled={quiz.isPending}>{quiz.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4" />}{quiz.isPending ? "Generating…" : quiz.data ? "Generate again" : "Generate Knowledge Check"}</Button>
        </form>

        {error && <div className="mt-4 rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive" role="alert">{error}</div>}
        {!quiz.data && !quiz.isPending && !error && <EmptyState className="mt-8" icon={BookOpenCheck} title="Knowledge Check" description="The existing quiz flow now lives inside Insights. Questions are generated only from authorized source content." />}
        {quiz.isPending && <div className="mt-8 flex items-center gap-3 rounded-lg border border-border bg-muted/20 p-6"><Loader2 className="h-5 w-5 animate-spin text-primary" /><div><p className="text-sm font-medium">Preparing your Knowledge Check</p><p className="text-xs text-muted-foreground">Reviewing bounded source evidence and writing grounded questions.</p></div></div>}

        {quiz.data && !quiz.isPending && <section className="mt-8"><div className="mb-4 flex flex-wrap items-center justify-between gap-3"><div><p className="text-xs font-semibold uppercase tracking-wider text-primary">Grounded Knowledge Check</p><h2 className="mt-1 text-xl font-semibold">{quiz.data.title}</h2></div><Button variant="outline" size="sm" onClick={() => quiz.reset()}><RefreshCw className="h-3.5 w-3.5" /> Start over</Button></div><ol className="space-y-4">{quiz.data.questions.map((question, index) => <li key={`${index}-${question.text}`} className="rounded-lg border border-border bg-card p-5"><p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">Question {index + 1}</p><h3 className="mt-2 text-sm font-semibold leading-6">{question.text}</h3><ul className="mt-4 grid gap-2 sm:grid-cols-2">{question.options.map((option) => <li key={option} className={`rounded-md border px-3 py-2.5 text-sm ${revealed.has(index) && option === question.correctAnswer ? "border-primary/40 bg-primary/5 text-foreground" : "border-border bg-background"}`}>{revealed.has(index) && option === question.correctAnswer && <CheckCircle2 className="mr-2 inline h-4 w-4 text-primary" />}{option}</li>)}</ul><Button className="mt-4" variant="outline" size="sm" onClick={() => toggleAnswer(index)}>{revealed.has(index) ? "Hide answer" : "Reveal answer"}</Button>{revealed.has(index) && <div className="mt-3 rounded-md bg-muted/40 p-4"><p className="text-sm font-medium">{question.correctAnswer}</p><p className="mt-1 text-sm leading-6 text-muted-foreground">{question.explanation}</p>{question.sourceIds.length > 0 && <p className="mt-2 text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Sources: {question.sourceIds.join(", ")}</p>}</div>}</li>)}</ol></section>}
      </div>
    </div>
  );
}
