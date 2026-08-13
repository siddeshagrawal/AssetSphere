import { AlertTriangle, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ApiError } from "@/types/api";

interface ErrorDisplayProps {
  error: unknown;
  onRetry?: () => void;
  title?: string;
}

/**
 * Page-level error display. Renders a typed message for ApiError instances,
 * a generic message for everything else.
 */
export function ErrorDisplay({ error, onRetry, title }: ErrorDisplayProps) {
  const heading = title ?? "Something went wrong";
  let message = "An unexpected error occurred. Please try again.";

  if (error instanceof ApiError) {
    if (error.status === 404) {
      message = "The requested resource could not be found.";
    } else if (error.status === 403) {
      message = "You do not have permission to access this.";
    } else if (error.status === 429) {
      message = "Too many requests. Please wait a moment before trying again.";
    } else if (error.status >= 500) {
      message = "The server is unavailable. Please try again shortly.";
    } else {
      message = error.message;
    }
  }

  return (
    <div className="flex flex-col items-center justify-center rounded-lg border border-border bg-muted/20 px-6 py-14 text-center">
      <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-destructive/10">
        <AlertTriangle className="h-6 w-6 text-destructive" aria-hidden="true" />
      </div>
      <h3 className="text-sm font-medium text-foreground">{heading}</h3>
      <p className="mt-1 max-w-sm text-sm text-muted-foreground">{message}</p>
      {onRetry && (
        <Button
          variant="outline"
          size="sm"
          className="mt-4 gap-2"
          onClick={onRetry}
        >
          <RefreshCw className="h-3.5 w-3.5" />
          Try again
        </Button>
      )}
    </div>
  );
}
