import { useNavigate } from "react-router-dom";
import { FileQuestion } from "lucide-react";
import { Button } from "@/components/ui/button";

export function NotFoundPage() {
  const navigate = useNavigate();

  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-4">
      <div className="flex flex-col items-center gap-4 text-center max-w-sm">
        <div className="flex h-14 w-14 items-center justify-center rounded-full bg-muted">
          <FileQuestion className="h-7 w-7 text-muted-foreground" aria-hidden="true" />
        </div>
        <div>
          <h1 className="text-xl font-semibold text-foreground">Page not found</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            The page you&apos;re looking for doesn&apos;t exist or has been moved.
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => navigate(-1)}>
            Go back
          </Button>
          <Button onClick={() => navigate("/workspaces")}>
            Go to workspaces
          </Button>
        </div>
      </div>
    </div>
  );
}
