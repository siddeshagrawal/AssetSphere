import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "@/features/auth/AuthProvider";
import { LoadingScreen } from "@/components/shared/LoadingScreen";

/**
 * Declarative auth guard.
 *
 * BOOTSTRAPPING  → full-screen loading (never flash protected UI)
 * UNAUTHENTICATED → redirect to /login with current location preserved
 * AUTHENTICATED  → render children via <Outlet>
 */
export function RequireAuth() {
  const { session } = useAuth();
  const location = useLocation();

  if (session.status === "BOOTSTRAPPING") {
    return <LoadingScreen />;
  }

  if (session.status === "UNAUTHENTICATED") {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return <Outlet />;
}
