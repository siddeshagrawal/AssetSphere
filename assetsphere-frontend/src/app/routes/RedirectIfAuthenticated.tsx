import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "@/features/auth/AuthProvider";
import { LoadingScreen } from "@/components/shared/LoadingScreen";

/**
 * Wraps public routes (login, register).
 * Redirects already-authenticated users away from auth pages.
 *
 * BOOTSTRAPPING  → wait (prevents flashing login while restoring session)
 * AUTHENTICATED  → redirect to /workspaces
 * UNAUTHENTICATED → render the public page via <Outlet>
 */
export function RedirectIfAuthenticated() {
  const { session } = useAuth();

  if (session.status === "BOOTSTRAPPING") {
    return <LoadingScreen />;
  }

  if (session.status === "AUTHENTICATED") {
    return <Navigate to="/workspaces" replace />;
  }

  return <Outlet />;
}
