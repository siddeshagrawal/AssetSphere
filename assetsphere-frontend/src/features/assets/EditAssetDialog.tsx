import { useEffect, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useUpdateAssetMetadata } from "@/features/assets/hooks";
import { ApiError } from "@/types/api";

interface EditAssetDialogProps {
  workspaceId: string;
  assetId: string;
  displayName: string;
  description: string | null;
  originalFilename: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function EditAssetDialog(props: EditAssetDialogProps) {
  const update = useUpdateAssetMetadata(props.workspaceId, props.assetId);
  const [displayName, setDisplayName] = useState(props.displayName);
  const [description, setDescription] = useState(props.description ?? "");

  useEffect(() => {
    if (props.open) {
      setDisplayName(props.displayName);
      setDescription(props.description ?? "");
      update.reset();
    }
  }, [props.open, props.displayName, props.description]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!displayName.trim()) return;
    try {
      await update.mutateAsync({ displayName: displayName.trim(), description: description.trim() || null });
      props.onOpenChange(false);
    } catch { /* error is rendered below */ }
  }

  const error = update.error instanceof ApiError ? update.error.message : update.error ? "Update failed." : null;
  return <Dialog open={props.open} onOpenChange={(open) => !update.isPending && props.onOpenChange(open)}><DialogContent><DialogHeader><DialogTitle>Edit asset details</DialogTitle><DialogDescription>Update the logical asset name and description. The original filename and version history remain unchanged.</DialogDescription></DialogHeader><form onSubmit={submit} className="space-y-4"><div className="space-y-2"><Label htmlFor="edit-display-name">Display name</Label><Input id="edit-display-name" value={displayName} maxLength={255} disabled={update.isPending} onChange={(event) => setDisplayName(event.target.value)} /></div><div className="space-y-2"><Label htmlFor="edit-description">Description</Label><textarea id="edit-description" value={description} maxLength={2000} disabled={update.isPending} onChange={(event) => setDescription(event.target.value)} className="min-h-24 w-full resize-y rounded-md border border-input bg-background px-3 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" placeholder="Optional context for your team" /></div><div className="rounded-md bg-muted/40 px-3 py-2 text-xs text-muted-foreground">Original filename: {props.originalFilename}</div>{error && <p role="alert" className="text-sm text-destructive">{error}</p>}<DialogFooter><Button type="button" variant="outline" disabled={update.isPending} onClick={() => props.onOpenChange(false)}>Cancel</Button><Button type="submit" disabled={update.isPending || !displayName.trim()}>{update.isPending ? "Saving…" : "Save changes"}</Button></DialogFooter></form></DialogContent></Dialog>;
}
