import { useState, type FormEvent } from "react";
import { UploadCloud } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useUploadAsset } from "@/features/assets/hooks";
import { FileDropzone, mediaUploadEntitlementError } from "@/features/assets/FileDropzone";
import { useWorkspaceBilling } from "@/features/billing/hooks";
import { ApiError } from "@/types/api";

interface UploadAssetDialogProps {
  workspaceId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function UploadAssetDialog({ workspaceId, open, onOpenChange }: UploadAssetDialogProps) {
  const upload = useUploadAsset(workspaceId);
  const billing = useWorkspaceBilling(workspaceId);
  const [file, setFile] = useState<File | null>(null);
  const [displayName, setDisplayName] = useState("");
  const [description, setDescription] = useState("");
  const [progress, setProgress] = useState(0);
  const [validationError, setValidationError] = useState<string | null>(null);

  function reset() {
    setFile(null);
    setDisplayName("");
    setDescription("");
    setProgress(0);
    setValidationError(null);
    upload.reset();
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!file) {
      setValidationError("Choose a file to upload.");
      return;
    }
    if (file.size > 25 * 1_024 * 1_024) {
      setValidationError("File size must not exceed 25 MB.");
      return;
    }
    const entitlementError = mediaUploadEntitlementError(file, billing.data?.entitlements);
    if (entitlementError) {
      setValidationError(entitlementError);
      return;
    }
    setValidationError(null);
    try {
      await upload.mutateAsync({ file, displayName, description, onProgress: setProgress });
      reset();
      onOpenChange(false);
    } catch {
      setProgress(0);
    }
  }

  const error = validationError ?? (upload.error instanceof ApiError
    ? upload.error.message
    : upload.error
      ? "Upload failed. Please try again."
      : null);

  return (
    <Dialog
      open={open}
      onOpenChange={(nextOpen) => {
        if (upload.isPending) return;
        if (!nextOpen) reset();
        onOpenChange(nextOpen);
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Upload asset</DialogTitle>
          <DialogDescription>
            Add a document, image, or video. Supported assets become searchable after processing; AI insights are generated on demand.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={submit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="asset-file">File</Label>
            <FileDropzone
              file={file}
              inputId="asset-file"
              disabled={upload.isPending}
              onFileChange={setFile}
              onValidationError={setValidationError}
              mediaEntitlements={billing.data?.entitlements}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="asset-display-name">Display name</Label>
            <Input
              id="asset-display-name"
              value={displayName}
              maxLength={160}
              disabled={upload.isPending}
              placeholder={file?.name ?? "Optional"}
              onChange={(event) => setDisplayName(event.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="asset-description">Description</Label>
            <textarea
              id="asset-description"
              value={description}
              maxLength={2_000}
              disabled={upload.isPending}
              placeholder="Optional context for this asset"
              onChange={(event) => setDescription(event.target.value)}
              className="min-h-20 w-full resize-y rounded-md border border-input bg-background px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-1 disabled:opacity-50"
            />
          </div>
          {upload.isPending && (
            <div className="space-y-1.5" aria-live="polite">
              <div className="flex justify-between text-xs text-muted-foreground">
                <span>Uploading</span>
                <span>{progress}%</span>
              </div>
              <div className="h-1.5 overflow-hidden rounded-full bg-muted">
                <div className="h-full bg-primary transition-all" style={{ width: `${progress}%` }} />
              </div>
            </div>
          )}
          {error && <p className="text-sm text-destructive" role="alert">{error}</p>}
          <DialogFooter>
            <Button type="button" variant="outline" disabled={upload.isPending} onClick={() => { reset(); onOpenChange(false); }}>
              Cancel
            </Button>
            <Button type="submit" disabled={upload.isPending}>
              <UploadCloud className="h-4 w-4" />
              {upload.isPending ? "Uploading…" : "Upload"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
