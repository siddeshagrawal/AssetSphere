import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Check, ChevronsUpDown, Plus } from "lucide-react";
import { useAuth } from "@/features/auth/AuthProvider";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

interface WorkspaceSelectorProps {
  onCreateClick: () => void;
}

/**
 * Workspace switcher shown in the sidebar.
 *
 * Workspace context is URL-owned (/workspaces/:workspaceId).
 * Selecting a workspace navigates to the new URL — no separate global state.
 */
export function WorkspaceSelector({ onCreateClick }: WorkspaceSelectorProps) {
  const { session } = useAuth();
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);

  const workspaces = session.status === "AUTHENTICATED" ? session.workspaces : [];
  const current = workspaces.find((w) => w.id === workspaceId);

  return (
    <div className="relative">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-label="Switch workspace"
        className={cn(
          "flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-sm",
          "text-sidebar-foreground hover:bg-sidebar-muted transition-colors",
          "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sidebar-accent-foreground"
        )}
      >
        {/* Workspace identity icon */}
        <span
          className="flex h-6 w-6 shrink-0 items-center justify-center rounded bg-sidebar-accent text-xs font-bold text-sidebar-accent-foreground uppercase"
          aria-hidden="true"
        >
          {current ? current.name.charAt(0) : "?"}
        </span>
        <span className="flex-1 truncate text-left font-medium">
          {current ? current.name : "Select workspace"}
        </span>
        <ChevronsUpDown className="h-3.5 w-3.5 shrink-0 opacity-50" aria-hidden="true" />
      </button>

      {open && (
        <>
          {/* Backdrop */}
          <div
            className="fixed inset-0 z-10"
            onClick={() => setOpen(false)}
            aria-hidden="true"
          />
          {/* Dropdown */}
          <div
            role="listbox"
            aria-label="Workspaces"
            className="absolute left-0 right-0 top-full z-20 mt-1 overflow-hidden rounded-md border border-sidebar-border bg-sidebar shadow-lg"
          >
            {workspaces.length > 0 ? (
              <ul className="max-h-60 overflow-y-auto py-1">
                {workspaces.map((ws) => (
                  <li key={ws.id}>
                    <button
                      type="button"
                      role="option"
                      aria-selected={ws.id === workspaceId}
                      onClick={() => {
                        setOpen(false);
                        navigate(`/workspaces/${ws.id}`);
                      }}
                      className={cn(
                        "flex w-full items-center gap-2 px-3 py-2 text-sm text-sidebar-foreground",
                        "hover:bg-sidebar-muted transition-colors",
                        "focus-visible:outline-none focus-visible:ring-inset focus-visible:ring-2 focus-visible:ring-sidebar-accent-foreground"
                      )}
                    >
                      <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded bg-sidebar-accent text-[10px] font-bold text-sidebar-accent-foreground uppercase">
                        {ws.name.charAt(0)}
                      </span>
                      <span className="flex-1 truncate">{ws.name}</span>
                      {ws.id === workspaceId && (
                        <Check className="h-3.5 w-3.5 text-sidebar-accent-foreground" aria-hidden="true" />
                      )}
                    </button>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="px-3 py-2 text-xs text-sidebar-muted-foreground">
                No workspaces yet.
              </p>
            )}

            <div className="border-t border-sidebar-border p-1">
              <Button
                variant="ghost"
                size="sm"
                className="w-full justify-start gap-2 text-sidebar-foreground hover:bg-sidebar-muted hover:text-sidebar-foreground"
                onClick={() => {
                  setOpen(false);
                  onCreateClick();
                }}
              >
                <Plus className="h-3.5 w-3.5" aria-hidden="true" />
                New workspace
              </Button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
