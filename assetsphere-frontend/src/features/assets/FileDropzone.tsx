import { useEffect, useRef, useState, type DragEvent } from "react";
import { FileText, UploadCloud, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn, formatBytes } from "@/lib/utils";
import type { PlanEntitlements } from "@/types/billing";

const ACCEPTED_MIME_TYPES = new Set([
  "application/pdf",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "text/plain",
  "text/markdown",
  "text/x-markdown",
  "text/csv",
  "application/csv",
  "application/vnd.ms-excel",
  "application/json",
  "text/json",
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  "application/vnd.openxmlformats-officedocument.presentationml.presentation",
  "image/png",
  "image/jpeg",
  "image/webp",
  "video/mp4",
  "video/webm",
]);
const ACCEPTED_EXTENSIONS = [
  ".pdf", ".docx", ".txt", ".md", ".csv", ".json", ".xlsx", ".pptx",
  ".png", ".jpg", ".jpeg", ".webp", ".mp4", ".webm",
];
const MAX_FILE_SIZE = 25 * 1_024 * 1_024;

interface FileDropzoneProps {
  file: File | null;
  inputId: string;
  disabled?: boolean;
  onFileChange: (file: File | null) => void;
  onValidationError: (message: string | null) => void;
  mediaEntitlements?: Pick<PlanEntitlements, "ocrEnabled" | "videoTranscriptionEnabled">;
}

export function mediaUploadEntitlementError(
  file: File,
  entitlements?: Pick<PlanEntitlements, "ocrEnabled" | "videoTranscriptionEnabled">
): string | null {
  if (!entitlements) return null;
  const filename = file.name.toLowerCase();
  const video = file.type === "video/mp4" || file.type === "video/webm"
    || filename.endsWith(".mp4") || filename.endsWith(".webm");
  if (video && !entitlements.videoTranscriptionEnabled) {
    return "Video transcription requires a PRO or ENTERPRISE plan. Upgrade this workspace to upload video.";
  }
  const image = file.type.startsWith("image/")
    || [".png", ".jpg", ".jpeg", ".webp"].some((extension) => filename.endsWith(extension));
  if (image && !entitlements.ocrEnabled) {
    return "Image OCR requires a PRO or ENTERPRISE plan. Upgrade this workspace to upload images.";
  }
  return null;
}

export function FileDropzone({
  file,
  inputId,
  disabled = false,
  onFileChange,
  onValidationError,
  mediaEntitlements,
}: FileDropzoneProps) {
  const input = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);

  useEffect(() => {
    if (!file && input.current) input.current.value = "";
  }, [file]);

  function select(candidate: File | null) {
    if (!candidate) return;
    const supported = ACCEPTED_MIME_TYPES.has(candidate.type)
      || ACCEPTED_EXTENSIONS.some((extension) => candidate.name.toLowerCase().endsWith(extension));
    if (!supported) {
      onValidationError("Choose a supported document, image, or video file.");
      return;
    }
    if (candidate.size > MAX_FILE_SIZE) {
      onValidationError("File size must not exceed 25 MB.");
      return;
    }
    const entitlementError = mediaUploadEntitlementError(candidate, mediaEntitlements);
    if (entitlementError) {
      onValidationError(entitlementError);
      return;
    }
    onFileChange(candidate);
    onValidationError(null);
  }

  function drop(event: DragEvent<HTMLButtonElement>) {
    event.preventDefault();
    setDragging(false);
    if (!disabled) select(event.dataTransfer.files?.[0] ?? null);
  }

  return (
    <>
      <input
        ref={input}
        id={inputId}
        type="file"
        className="sr-only"
        accept=".pdf,.docx,.txt,.md,.csv,.json,.xlsx,.pptx,.png,.jpg,.jpeg,.webp,.mp4,.webm,image/png,image/jpeg,image/webp,video/mp4,video/webm"
        disabled={disabled}
        onChange={(event) => select(event.target.files?.[0] ?? null)}
      />
      {file ? (
        <div className="flex items-center gap-3 rounded-lg border border-border bg-muted/25 p-3">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-background shadow-sm">
            <FileText className="h-5 w-5 text-muted-foreground" />
          </div>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium text-foreground">{file.name}</p>
            <p className="mt-0.5 text-xs text-muted-foreground">{formatBytes(file.size)}</p>
          </div>
          <Button type="button" variant="ghost" size="sm" disabled={disabled} onClick={() => input.current?.click()}>
            Change
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            disabled={disabled}
            onClick={() => { onFileChange(null); onValidationError(null); }}
            aria-label="Remove selected file"
          >
            <X className="h-4 w-4" />
          </Button>
        </div>
      ) : (
        <button
          type="button"
          disabled={disabled}
          onClick={() => input.current?.click()}
          onDragEnter={(event) => { event.preventDefault(); setDragging(true); }}
          onDragOver={(event) => event.preventDefault()}
          onDragLeave={() => setDragging(false)}
          onDrop={drop}
          className={cn(
            "flex w-full flex-col items-center justify-center rounded-lg border border-dashed px-5 py-7 text-center transition-colors",
            "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-1 disabled:opacity-50",
            dragging ? "border-primary bg-primary/5" : "border-border bg-muted/20 hover:border-primary/50 hover:bg-muted/40"
          )}
          aria-describedby={`${inputId}-help`}
        >
          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-background shadow-sm">
            <UploadCloud className="h-5 w-5 text-muted-foreground" />
          </div>
          <p className="mt-3 text-sm font-medium text-foreground">Drop a file here or click to browse</p>
          <p id={`${inputId}-help`} className="mt-1 text-xs text-muted-foreground">
            PDF, DOCX, TXT, MD, CSV, JSON, XLSX, PPTX, PNG, JPEG, WebP, MP4, or WebM up to 25 MB
          </p>
        </button>
      )}
    </>
  );
}
