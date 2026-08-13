import { useState } from "react";
import { Link, NavLink, useParams } from "react-router-dom";
import {
  LayoutDashboard,
  FolderOpen,
  Search,
  MessageSquare,
  Users,
  Settings,
  Layers,
  LogOut,
  ChevronLeft,
  ChevronRight,
  CreditCard,
  BookOpenCheck,
} from "lucide-react";
import { useAuth, useCurrentUser } from "@/features/auth/AuthProvider";
import { useLogout } from "@/features/auth/hooks";
import { WorkspaceSelector } from "./WorkspaceSelector";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Separator } from "@/components/ui/separator";
import { cn } from "@/lib/utils";

interface NavItem {
  label: string;
  icon: typeof LayoutDashboard;
  to: string;
  disabled?: boolean;
  badge?: string;
}

interface SidebarProps {
  onCreateWorkspace: () => void;
  onNavigate?: () => void;
}

export function Sidebar({ onCreateWorkspace, onNavigate }: SidebarProps) {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const user = useCurrentUser();
  const { session } = useAuth();
  const logoutMutation = useLogout();
  const [collapsed, setCollapsed] = useState(false);

  const workspaces = session.status === "AUTHENTICATED" ? session.workspaces : [];
  const currentWorkspace = workspaces.find((w) => w.id === workspaceId);

  const navItems: NavItem[] = workspaceId
    ? [
        {
          label: "Overview",
          icon: LayoutDashboard,
          to: `/workspaces/${workspaceId}`,
        },
        {
          label: "Assets",
          icon: FolderOpen,
          to: `/workspaces/${workspaceId}/assets`,
        },
        {
          label: "Search",
          icon: Search,
          to: `/workspaces/${workspaceId}/search`,
        },
        {
          label: "Ask AssetSphere",
          icon: MessageSquare,
          to: `/workspaces/${workspaceId}/ask`,
        },
        {
          label: "Insights",
          icon: BookOpenCheck,
          to: `/workspaces/${workspaceId}/insights`,
        },
        {
          label: "Members",
          icon: Users,
          to: `/workspaces/${workspaceId}/members`,
        },
        {
          label: "Billing & plan",
          icon: CreditCard,
          to: `/workspaces/${workspaceId}/billing`,
        },
        {
          label: "Settings",
          icon: Settings,
          to: `/workspaces/${workspaceId}/settings`,
        },
      ]
    : [];

  const initials = user.displayName
    .split(" ")
    .map((n) => n[0])
    .join("")
    .toUpperCase()
    .slice(0, 2);

  return (
    <aside
      className={cn(
        "relative flex h-full flex-col bg-sidebar border-r border-sidebar-border transition-all duration-200",
        collapsed ? "w-14" : "w-72 max-w-[85vw] md:w-56"
      )}
      aria-label="Main navigation"
    >
      {/* Logo row */}
      <div className={cn(
        "flex h-14 shrink-0 items-center border-b border-sidebar-border px-3",
        collapsed ? "justify-center" : "justify-between"
      )}>
        <Link to={workspaceId ? `/workspaces/${workspaceId}` : "/workspaces"} className="flex items-center gap-2 rounded-md" aria-label="AssetSphere workspace home">
          <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-primary">
            <Layers className="h-4 w-4 text-primary-foreground" aria-hidden="true" />
          </div>
          {!collapsed && (
            <span className="text-sm font-semibold text-sidebar-foreground">
              AssetSphere
            </span>
          )}
        </Link>
        {!collapsed && (
          <button
            type="button"
            onClick={() => setCollapsed(true)}
            className="rounded-md p-1 text-sidebar-muted-foreground hover:text-sidebar-foreground hover:bg-sidebar-muted transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sidebar-accent-foreground"
            aria-label="Collapse sidebar"
          >
            <ChevronLeft className="h-4 w-4" />
          </button>
        )}
        {collapsed && (
          <button
            type="button"
            onClick={() => setCollapsed(false)}
            className="absolute -right-3 top-4 flex h-6 w-6 items-center justify-center rounded-full border border-sidebar-border bg-sidebar text-sidebar-muted-foreground hover:text-sidebar-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sidebar-accent-foreground"
            aria-label="Expand sidebar"
          >
            <ChevronRight className="h-3 w-3" />
          </button>
        )}
      </div>

      {/* Workspace selector */}
      {!collapsed && (
        <div className="px-2 py-3 border-b border-sidebar-border">
          <WorkspaceSelector onCreateClick={onCreateWorkspace} />
          {currentWorkspace && (
            <Link to={`/workspaces/${currentWorkspace.id}`} className="mt-1.5 block rounded px-2 text-[10px] uppercase tracking-wider text-sidebar-muted-foreground hover:text-sidebar-foreground">{currentWorkspace.role} · Workspace overview</Link>
          )}
        </div>
      )}

      {/* Navigation */}
      <nav className="flex-1 overflow-y-auto px-2 py-3" aria-label="Workspace navigation">
        {navItems.length > 0 ? (
          <ul className="space-y-0.5" role="list">
            {navItems.map((item) => (
              <li key={item.to}>
                {item.disabled ? (
                  <div
                    className={cn(
                      "flex items-center gap-2.5 rounded-md px-2 py-1.5 text-sm",
                      "text-sidebar-muted-foreground opacity-50 cursor-not-allowed select-none"
                    )}
                    aria-disabled="true"
                    title={`${item.label} — available in next checkpoint`}
                  >
                    <item.icon className="h-4 w-4 shrink-0" aria-hidden="true" />
                    {!collapsed && (
                      <>
                        <span className="flex-1 truncate">{item.label}</span>
                        {item.badge && (
                          <span className="text-[9px] font-medium uppercase tracking-wider px-1.5 py-0.5 rounded bg-sidebar-muted text-sidebar-muted-foreground">
                            {item.badge}
                          </span>
                        )}
                      </>
                    )}
                  </div>
                ) : (
                  <NavLink
                    to={item.to}
                    end={item.label === "Overview"}
                    onClick={onNavigate}
                    className={({ isActive }) =>
                      cn(
                        "flex min-h-11 items-center gap-2.5 rounded-md px-2 py-2 text-sm transition-colors md:min-h-0 md:py-1.5",
                        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sidebar-accent-foreground",
                        isActive
                          ? "bg-sidebar-accent text-sidebar-accent-foreground font-medium"
                          : "text-sidebar-foreground hover:bg-sidebar-muted"
                      )
                    }
                    aria-label={item.label}
                  >
                    <item.icon className="h-4 w-4 shrink-0" aria-hidden="true" />
                    {!collapsed && (
                      <span className="flex-1 truncate">{item.label}</span>
                    )}
                  </NavLink>
                )}
              </li>
            ))}
          </ul>
        ) : (
          !collapsed && (
            <p className="px-2 text-xs text-sidebar-muted-foreground">
              Select a workspace to navigate.
            </p>
          )
        )}
      </nav>

      {/* User footer */}
      <div className="shrink-0 border-t border-sidebar-border">
        <Separator className="bg-sidebar-border" />
        <div className="p-2">
          <div
            className={cn(
              "flex items-center gap-2 rounded-md px-2 py-2",
              collapsed && "justify-center"
            )}
          >
            <Avatar className="h-7 w-7 shrink-0">
              <AvatarFallback className="bg-sidebar-accent text-sidebar-accent-foreground text-xs font-semibold">
                {initials}
              </AvatarFallback>
            </Avatar>
            {!collapsed && (
              <div className="flex-1 min-w-0">
                <p className="text-xs font-medium text-sidebar-foreground truncate">
                  {user.displayName}
                </p>
                <p className="text-[10px] text-sidebar-muted-foreground truncate">
                  {user.email}
                </p>
              </div>
            )}
            {!collapsed && (
              <button
                type="button"
                onClick={() => logoutMutation.mutate()}
                disabled={logoutMutation.isPending}
                className={cn(
                  "rounded-md p-1.5 text-sidebar-muted-foreground hover:text-sidebar-foreground hover:bg-sidebar-muted transition-colors",
                  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sidebar-accent-foreground"
                )}
                aria-label="Sign out"
                title="Sign out"
              >
                <LogOut className="h-3.5 w-3.5" aria-hidden="true" />
              </button>
            )}
          </div>
        </div>
      </div>
    </aside>
  );
}
