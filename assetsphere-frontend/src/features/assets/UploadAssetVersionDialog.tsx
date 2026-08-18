import { useRef, useState, type FormEvent } from "react";
import { History, UploadCloud } from "lucide-react";
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
import { FileDropzone, mediaUploadEntitlementError } from "@/features/assets/FileDropzone";
import { useWorkspaceBilling } from "@/features/billing/hooks";
import { useUploadAssetVersion } from "@/features/assets/hooks";
import { ApiError } from "@/types/api";
import { createClientRequestId } from "@/lib/utils";

interface UploadAssetVersionDialogProps {
  workspaceId: string;
  assetId: string;
  currentVersion: number;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onUploaded?: (versionNumber: number) => void;
}

export function UploadAssetVersionDialog({
  workspaceId,
  assetId,
  currentVersion,
  open,
  onOpenChange,
  onUploaded,
}: UploadAssetVersionDialogProps) {
  const upload = useUploadAssetVersion(workspaceId, assetId);
  const billing = useWorkspaceBilling(workspaceId);
  const idempotencyKey = useRef(createClientRequestId());
  const [file, setFile] = useState<File | null>(null);
  const [progress, setProgress] = useState(0);
  const [validationError, setValidationError] = useState<string | null>(null);

  function reset() {
    setFile(null);
    setProgress(0);
    setValidationError(null);
    idempotencyKey.current = createClientRequestId();
    upload.reset();
  }

  function changeFile(nextFile: File | null) {
    setFile(nextFile);
    idempotencyKey.current = createClientRequestId();
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!file) {
      setValidationError("Choose a file to upload.");
      return;
    }
    const entitlementError = mediaUploadEntitlementError(file, billing.data?.entitlements);
    if (entitlementError) {
      setValidationError(entitlementError);
      return;
    }
    try {
      const uploaded = await upload.mutateAsync({ file, idempotencyKey: idempotencyKey.current, onProgress: setProgress });
      onUploaded?.(uploaded.versionNumber);
      reset();
      onOpenChange(false);
    } catch {
      setProgress(0);
    }
  }

  const error = validationError ?? (upload.error instanceof ApiError
    ? upload.error.message
    : upload.error
      ? "Version upload failed. Please try again."
      : null);

  return (
    <Dialog open={open} onOpenChange={(nextOpen) => {
      if (upload.isPending) return;
      if (!nextOpen) reset();
      onOpenChange(nextOpen);
    }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Upload new version</DialogTitle>
          <DialogDescription>
            This advances the asset from Version {currentVersion} while preserving every earlier version in history.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={submit} className="space-y-4">
          <div className="rounded-lg border border-border bg-muted/25 p-3 text-sm text-muted-foreground">
            <div className="flex gap-2">
              <History className="mt-0.5 h-4 w-4 shrink-0" />
              The new file becomes current after upload. Search becomes available after processing; AI insights are generated on demand.
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="asset-version-file">Replacement file</Label>
            <FileDropzone
              file={file}
              inputId="asset-version-file"
              disabled={upload.isPending}
              onFileChange={changeFile}
              onValidationError={setValidationError}
              mediaEntitlements={billing.data?.entitlements}
            />
          </div>
          {upload.isPending && (
            <div className="space-y-1.5" aria-live="polite">
              <div className="flex justify-between text-xs text-muted-foreground"><span>Uploading version</span><span>{progress}%</span></div>
              <div className="h-1.5 overflow-hidden rounded-full bg-muted"><div className="h-full bg-primary transition-all" style={{ width: `${progress}%` }} /></div>
            </div>
          )}
          {error && <p className="text-sm text-destructive" role="alert">{error}</p>}
          <DialogFooter>
            <Button type="button" variant="outline" disabled={upload.isPending} onClick={() => { reset(); onOpenChange(false); }}>Cancel</Button>
            <Button type="submit" disabled={upload.isPending || !file}>
              <UploadCloud className="h-4 w-4" /> {upload.isPending ? "Uploading…" : `Upload Version ${currentVersion + 1}`}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
