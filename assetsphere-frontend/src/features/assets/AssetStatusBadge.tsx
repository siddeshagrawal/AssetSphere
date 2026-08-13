import type { AssetProcessingStatus } from "@/types/asset";

const DISPLAY: Record<AssetProcessingStatus, { label: string; className: string }> = {
  UPLOADED: { label: "Uploaded", className: "bg-slate-500/10 text-slate-700" },
  QUEUED: { label: "Queued", className: "bg-amber-500/10 text-amber-700" },
  PROCESSING: { label: "Processing", className: "bg-blue-500/10 text-blue-700" },
  READY: { label: "Ready", className: "bg-emerald-500/10 text-emerald-700" },
  PARTIALLY_PROCESSED: { label: "Partial", className: "bg-amber-500/10 text-amber-700" },
  FAILED: { label: "Failed", className: "bg-destructive/10 text-destructive" },
};

export function AssetStatusBadge({ status }: { status: AssetProcessingStatus }) {
  const display = DISPLAY[status];
  return (
    <span className={`rounded px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wider ${display.className}`}>
      {display.label}
    </span>
  );
}
