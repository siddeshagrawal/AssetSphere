import { Layers } from "lucide-react";

/**
 * Full-screen loading state shown during session bootstrap.
 * Prevents any protected UI from flashing before the session resolves.
 */
export function LoadingScreen() {
  return (
    <div
      className="fixed inset-0 flex items-center justify-center bg-background"
      role="status"
      aria-label="Loading AssetSphere"
    >
      <div className="flex flex-col items-center gap-3">
        <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary">
          <Layers className="h-5 w-5 text-primary-foreground" aria-hidden="true" />
        </div>
        <div className="flex items-center gap-1.5">
          <div className="h-1.5 w-1.5 animate-bounce rounded-full bg-muted-foreground [animation-delay:-0.3s]" />
          <div className="h-1.5 w-1.5 animate-bounce rounded-full bg-muted-foreground [animation-delay:-0.15s]" />
          <div className="h-1.5 w-1.5 animate-bounce rounded-full bg-muted-foreground" />
        </div>
      </div>
    </div>
  );
}
