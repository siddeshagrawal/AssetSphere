import { useEffect, useState, type FormEvent } from "react";
import {
  AlertTriangle,
  ArrowRight,
  GitCompareArrows,
  Loader2,
  Plus,
  Sparkles,
  Trash2,
  type LucideIcon,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { useCompareAssetVersions } from "@/features/assets/hooks";
import type { AssetEvolutionResponse, AssetVersionResponse } from "@/types/asset";
import { ApiError } from "@/types/api";
import { useAiModels } from "@/features/quiz-hooks";

interface CompareVersionsDialogProps {
  workspaceId: string;
  assetId: string;
  versions: AssetVersionResponse[];
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function CompareVersionsDialog({ workspaceId, assetId, versions, open, onOpenChange }: CompareVersionsDialogProps) {
  const compare = useCompareAssetVersions(workspaceId, assetId);
  const models = useAiModels(workspaceId);
  const [modelId, setModelId] = useState("");
  const [fromVersion, setFromVersion] = useState(versions[1]?.versionNumber ?? versions[0]?.versionNumber ?? 1);
  const [toVersion, setToVersion] = useState(versions[0]?.versionNumber ?? 1);

  useEffect(() => {
    if (open) {
      setFromVersion(versions[1]?.versionNumber ?? versions[0]?.versionNumber ?? 1);
      setToVersion(versions[0]?.versionNumber ?? 1);
      compare.reset();
    }
  }, [open, versions]);

  function changeFrom(version: number) {
    compare.reset();
    setFromVersion(version);
  }

  function changeTo(version: number) {
    compare.reset();
    setToVersion(version);
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (fromVersion !== toVersion) compare.mutate({ fromVersion, toVersion, modelId: modelId || undefined });
  }

  const error = compare.error instanceof ApiError
    ? compare.error.message
    : compare.error
      ? "Comparison is unavailable right now."
      : null;

  return (
    <Dialog open={open} onOpenChange={(next) => !compare.isPending && onOpenChange(next)}>
      <DialogContent className="flex max-h-[calc(100dvh-2rem)] max-w-2xl flex-col overflow-hidden">
        <DialogHeader className="shrink-0 pr-7">
          <DialogTitle className="flex items-center gap-2">
            <GitCompareArrows className="h-5 w-5 text-primary" /> Evolution Intelligence
          </DialogTitle>
          <DialogDescription>Compare two exact versions to understand how this asset&apos;s knowledge evolved.</DialogDescription>
        </DialogHeader>
        <form onSubmit={submit} className="flex min-h-0 flex-1 flex-col">
          <div className="min-h-0 flex-1 space-y-5 overflow-y-auto px-1">
            <div className="grid items-end gap-3 sm:grid-cols-[1fr_auto_1fr]">
              <VersionSelect label="From" value={fromVersion} versions={versions} disabled={compare.isPending} onChange={changeFrom} />
              <ArrowRight className="mb-2 hidden h-4 w-4 text-muted-foreground sm:block" />
              <VersionSelect label="To" value={toVersion} versions={versions} disabled={compare.isPending} onChange={changeTo} />
            </div>
            <div><Label htmlFor="evolution-model">AI model</Label><select id="evolution-model" value={modelId} onChange={(event) => { compare.reset(); setModelId(event.target.value); }} disabled={compare.isPending} className="mt-2 h-9 w-full rounded-md border border-input bg-background px-3 text-sm"><option value="">Plan default</option>{models.data?.filter((model) => model.capabilities.includes("EVOLUTION")).map((model) => <option key={model.modelId} value={model.modelId}>{model.displayName}</option>)}</select></div>
            {fromVersion === toVersion && <p className="text-sm text-destructive" role="alert">Choose two different versions.</p>}
            {compare.isPending && (
              <div className="flex items-center gap-3 rounded-lg bg-primary/5 p-5">
                <Loader2 className="h-5 w-5 animate-spin text-primary" />
                <div><p className="text-sm font-medium">Understanding what changed</p><p className="mt-1 text-xs text-muted-foreground">Both selected versions are being compared from their processed content.</p></div>
              </div>
            )}
            {error && <div className="flex gap-2 rounded-lg bg-destructive/5 p-4 text-sm text-destructive"><AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />{error}</div>}
            {compare.data && <EvolutionResult result={compare.data} />}
          </div>
          <DialogFooter className="shrink-0 border-t border-border bg-background pt-4">
            <Button type="button" variant="outline" disabled={compare.isPending} onClick={() => onOpenChange(false)}>Close</Button>
            <Button type="submit" disabled={compare.isPending || fromVersion === toVersion}>
              {compare.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4" />}
              Compare versions
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function VersionSelect({ label, value, versions, disabled, onChange }: {
  label: string;
  value: number;
  versions: AssetVersionResponse[];
  disabled: boolean;
  onChange: (value: number) => void;
}) {
  return (
    <div className="space-y-2">
      <Label>{label}</Label>
      <select
        value={value}
        disabled={disabled}
        onChange={(event) => onChange(Number(event.target.value))}
        className="h-9 w-full rounded-md border border-input bg-background px-3 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50"
      >
        {versions.map((version) => <option key={version.assetVersionId} value={version.versionNumber}>Version {version.versionNumber} — {version.originalFilename}</option>)}
      </select>
    </div>
  );
}

function EvolutionResult({ result }: { result: AssetEvolutionResponse }) {
  return (
    <div className="space-y-5 rounded-xl border border-primary/20 bg-primary/[0.03] p-5">
      <div><p className="text-[10px] font-semibold uppercase tracking-[0.16em] text-primary">Version {result.fromVersion} → Version {result.toVersion}</p><h3 className="mt-2 text-sm font-semibold">What changed</h3><p className="mt-2 text-sm leading-6 text-muted-foreground">{result.executiveSummary}</p></div>
      <ChangeList title="Key changes" icon={GitCompareArrows} items={result.keyChanges} />
      <div className="grid gap-4 sm:grid-cols-2"><ChangeList title="Additions" icon={Plus} items={result.additions} /><ChangeList title="Removals" icon={Trash2} items={result.removals} /></div>
      <ChangeList title="Important changes" icon={AlertTriangle} items={result.importantChanges} />
    </div>
  );
}

function ChangeList({ title, icon: Icon, items }: { title: string; icon: LucideIcon; items: string[] }) {
  if (items.length === 0) return null;
  return <div><h4 className="flex items-center gap-2 text-xs font-semibold"><Icon className="h-3.5 w-3.5 text-primary" />{title}</h4><ul className="mt-2 space-y-1.5">{items.map((item, index) => <li key={`${title}-${index}`} className="flex gap-2 text-sm leading-5 text-muted-foreground"><span className="mt-2 h-1 w-1 shrink-0 rounded-full bg-primary" />{item}</li>)}</ul></div>;
}
