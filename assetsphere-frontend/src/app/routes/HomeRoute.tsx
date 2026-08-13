import { Navigate } from "react-router-dom";
import { LoadingScreen } from "@/components/shared/LoadingScreen";
import { useAuth } from "@/features/auth/AuthProvider";
import { LandingPage } from "@/pages/landing/LandingPage";

export function HomeRoute() {
  const { session } = useAuth();
  if (session.status === "BOOTSTRAPPING") return <LoadingScreen />;
  if (session.status === "AUTHENTICATED") return <Navigate to="/workspaces" replace />;
  return <LandingPage />;
}
