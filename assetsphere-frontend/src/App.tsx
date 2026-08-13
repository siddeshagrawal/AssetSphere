import { BrowserRouter, Routes, Route } from "react-router-dom";
import { QueryClientProvider } from "@tanstack/react-query";
import { Toaster } from "sonner";

import { queryClient } from "@/lib/query-client";
import { AuthProvider } from "@/features/auth/AuthProvider";
import { RequireAuth } from "@/app/routes/RequireAuth";
import { RedirectIfAuthenticated } from "@/app/routes/RedirectIfAuthenticated";
import { HomeRoute } from "@/app/routes/HomeRoute";
import { AppShell } from "@/components/layout/AppShell";

import { LoginPage } from "@/pages/auth/LoginPage";
import { RegisterPage } from "@/pages/auth/RegisterPage";
import { WorkspaceSelectPage } from "@/pages/workspaces/WorkspaceSelectPage";
import { WorkspaceOverviewPage } from "@/pages/workspaces/WorkspaceOverviewPage";
import { AssetsPage } from "@/pages/assets/AssetsPage";
import { AssetDetailPage } from "@/pages/assets/AssetDetailPage";
import { SearchPage } from "@/pages/search/SearchPage";
import { AskPage } from "@/pages/ask/AskPage";
import { MembersPage } from "@/pages/members/MembersPage";
import { WorkspaceSettingsPage } from "@/pages/settings/WorkspaceSettingsPage";
import { AcceptInvitationPage } from "@/pages/invitations/AcceptInvitationPage";
import { NotFoundPage } from "@/pages/NotFoundPage";
import { BillingPage } from "@/pages/billing/BillingPage";
import { LocalCheckoutPage } from "@/pages/billing/LocalCheckoutPage";
import { QuizPage } from "@/pages/QuizPage";
import { OAuthCallbackPage } from "@/pages/auth/OAuthCallbackPage";
import { InsightsPage } from "@/pages/insights/InsightsPage";

/**
 * App — composition only.
 *
 * Providers:
 *   QueryClientProvider → TanStack Query
 *   BrowserRouter       → React Router
 *   AuthProvider        → session state (must be inside Router for navigate())
 *   Toaster             → sonner notifications
 *
 * Route structure:
 *   Public: / landing page; /login and /register auth routes
 *   Protected (RequireAuth guards):
 *     /workspaces                               — workspace selector
 *     /workspaces/:workspaceId                  — AppShell + workspace routes
 *   * → 404
 */
export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <Routes>
            <Route path="/" element={<HomeRoute />} />
            <Route path="/oauth/callback" element={<OAuthCallbackPage />} />
            <Route path="/invitations/accept" element={<AcceptInvitationPage />} />

            {/* Public auth routes — redirect to /workspaces if already authenticated */}
            <Route element={<RedirectIfAuthenticated />}>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />
            </Route>

            {/* Protected routes — redirect to /login if not authenticated */}
            <Route element={<RequireAuth />}>
              {/* Workspace selection page (no AppShell — full-width layout) */}
              <Route path="/workspaces" element={<WorkspaceSelectPage />} />

              {/* Workspace-scoped routes inside AppShell */}
              <Route path="/workspaces/:workspaceId" element={<AppShell />}>
                <Route index element={<WorkspaceOverviewPage />} />
                <Route path="assets" element={<AssetsPage />} />
                <Route path="assets/:assetId" element={<AssetDetailPage />} />
                <Route path="search" element={<SearchPage />} />
                <Route path="ask" element={<AskPage />} />
                <Route path="quiz" element={<QuizPage />} />
                <Route path="insights" element={<InsightsPage />} />
                <Route path="members" element={<MembersPage />} />
                <Route path="settings" element={<WorkspaceSettingsPage />} />
                <Route path="billing" element={<BillingPage />} />
                <Route path="billing/checkout/local" element={<LocalCheckoutPage />} />
              </Route>
            </Route>

            {/* 404 */}
            <Route path="*" element={<NotFoundPage />} />
          </Routes>

          {/* Sonner toast notifications — rendered at root level */}
          <Toaster position="bottom-right" richColors closeButton />
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
