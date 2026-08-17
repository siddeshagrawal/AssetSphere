import { useEffect, useRef, useState } from "react";
import { Link, Outlet, useParams } from "react-router-dom";
import { Layers, Menu, X } from "lucide-react";
import { Sidebar } from "./Sidebar";
import { CreateWorkspaceDialog } from "@/features/workspaces/CreateWorkspaceDialog";

/**
 * AppShell wraps all authenticated workspace routes.
 * Provides the sidebar, main content area, and the create-workspace dialog
 * accessible from anywhere in the shell (sidebar "New workspace" button).
 */
export function AppShell() {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const [createOpen, setCreateOpen] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const menuButton = useRef<HTMLButtonElement>(null);
  const mobileDrawer = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!mobileOpen) return;
    const focusable = mobileDrawer.current?.querySelectorAll<HTMLElement>(
      'a[href], button:not([disabled]), select, input, [tabindex]:not([tabindex="-1"])'
    );
    focusable?.[0]?.focus();
    const close = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setMobileOpen(false);
        menuButton.current?.focus();
      } else if (event.key === "Tab" && focusable?.length) {
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault();
          last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault();
          first.focus();
        }
      }
    };
    document.addEventListener("keydown", close);
    return () => document.removeEventListener("keydown", close);
  }, [mobileOpen]);

  return (
    <div className="flex h-[100dvh] overflow-hidden bg-background">
      <div className="hidden h-full md:block">
        <Sidebar onCreateWorkspace={() => setCreateOpen(true)} />
      </div>

      {mobileOpen && (
        <div className="fixed inset-0 z-40 flex md:hidden" role="dialog" aria-modal="true" aria-label="Workspace navigation">
          <div ref={mobileDrawer} className="h-full max-w-[85vw] shadow-xl"><Sidebar onCreateWorkspace={() => setCreateOpen(true)} onNavigate={() => setMobileOpen(false)} /></div>
          <button type="button" className="flex-1 bg-black/40" aria-label="Close navigation" onClick={() => setMobileOpen(false)} />
        </div>
      )}

      <main className="flex flex-1 flex-col overflow-hidden">
        <header className="flex min-h-14 shrink-0 items-center justify-between border-b border-border px-4 pt-[env(safe-area-inset-top)] md:hidden">
          <Link to={workspaceId ? `/workspaces/${workspaceId}` : "/workspaces"} className="flex items-center gap-2 rounded-md"><div className="flex h-7 w-7 items-center justify-center rounded-md bg-primary"><Layers className="h-4 w-4 text-primary-foreground" /></div><span className="text-sm font-semibold">AssetSphere</span></Link>
          <button ref={menuButton} type="button" className="flex min-h-11 min-w-11 items-center justify-center rounded-md p-2 text-muted-foreground hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" aria-expanded={mobileOpen} aria-label={mobileOpen ? "Close navigation" : "Open navigation"} onClick={() => setMobileOpen((value) => !value)}>{mobileOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}</button>
        </header>
        <div className="min-w-0 flex-1 overflow-x-hidden overflow-y-auto pb-[env(safe-area-inset-bottom)]">
          <Outlet />
        </div>
      </main>

      <CreateWorkspaceDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
      />
    </div>
  );
}
