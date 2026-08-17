import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { BillingPage } from "@/pages/billing/BillingPage";
import type { BillingResponse } from "@/types/billing";

const mocks = vi.hoisted(() => ({
  billing: {
    plan: "PRO", subscriptionStatus: "ACTIVE", periodStart: "2026-08-12T10:15:30Z",
    periodEnd: "2026-09-12T10:15:30Z", latestPaymentStatus: "PAID",
    paymentProvider: "RAZORPAY_LOCAL", autoRenew: false, cancelAtPeriodEnd: false,
    entitlements: { maxAssets: 1000, maxStorageBytes: 1000, maxMembers: 20, monthlyAiInsights: 500,
      monthlyAskRequests: 2000, monthlyEvolutionComparisons: 250, monthlySemanticSearches: 20000,
      monthlyQuizGenerations: 200, ocrEnabled: true, videoTranscriptionEnabled: true, fullAuditEnabled: true },
    usage: { assets: 1, storageBytes: 1, aiInsights: 1, askRequests: 1, evolutionComparisons: 1, quizGenerations: 1 },
    remaining: { assets: 999, storageBytes: 999, aiInsights: 499, askRequests: 1999, evolutionComparisons: 249, quizGenerations: 199 },
  } as BillingResponse,
}));

vi.mock("@/features/billing/hooks", () => ({
  useWorkspaceBilling: () => ({ data: mocks.billing, isLoading: false, isError: false }),
  usePaymentCapabilities: () => ({ data: { supportsOrderCreation: true }, isError: false }),
  usePlans: () => ({ data: undefined }),
  useCreateProCheckout: () => ({ isPending: false, mutateAsync: vi.fn() }),
  useCancelSubscription: () => ({ isPending: false, error: null, mutate: vi.fn() }),
}));

vi.mock("@/features/auth/AuthProvider", () => ({
  useAuth: () => ({
    session: {
      status: "AUTHENTICATED",
      workspaces: [{ id: "workspace-1", role: "OWNER" }],
    },
  }),
}));

describe("billing subscription display", () => {
  beforeEach(() => {
    mocks.billing = {
      ...mocks.billing,
      plan: "PRO",
      subscriptionStatus: "ACTIVE",
      paymentProvider: "RAZORPAY_LOCAL",
      autoRenew: false,
      cancelAtPeriodEnd: false,
    };
  });

  it("shows authoritative local PRO state without stale payment warnings", () => {
    render(<MemoryRouter initialEntries={["/workspaces/workspace-1/billing"]}><Routes>
      <Route path="/workspaces/:workspaceId/billing" element={<BillingPage />} />
    </Routes></MemoryRouter>);

    expect(screen.getByText(/Manual renewal/i)).toBeInTheDocument();
    expect(screen.queryByText(/Usage resets/i)).not.toBeInTheDocument();
    expect(screen.getByText("Knowledge Checks")).toBeInTheDocument();
    expect(screen.queryByText(/last payment was not completed/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/awaiting verified provider confirmation/i)).not.toBeInTheDocument();
  });

  it("treats Stripe success query as informational rather than subscription authority", () => {
    render(<MemoryRouter initialEntries={["/workspaces/workspace-1/billing?checkout=success"]}><Routes>
      <Route path="/workspaces/:workspaceId/billing" element={<BillingPage />} />
    </Routes></MemoryRouter>);

    expect(screen.getByText(/Subscription status updates after verified Stripe confirmation/i)).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "PRO" })).toBeInTheDocument();
  });

  it("labels FREE periods as usage resets", () => {
    mocks.billing = { ...mocks.billing, plan: "FREE", paymentProvider: null };

    render(<MemoryRouter initialEntries={["/workspaces/workspace-1/billing"]}><Routes>
      <Route path="/workspaces/:workspaceId/billing" element={<BillingPage />} />
    </Routes></MemoryRouter>);

    expect(screen.getByText(/Usage resets/i)).toBeInTheDocument();
    expect(screen.queryByText(/Renews/i)).not.toBeInTheDocument();
  });

  it("labels active Stripe PRO periods as renewals", () => {
    mocks.billing = { ...mocks.billing, paymentProvider: "STRIPE", autoRenew: true };

    render(<MemoryRouter initialEntries={["/workspaces/workspace-1/billing"]}><Routes>
      <Route path="/workspaces/:workspaceId/billing" element={<BillingPage />} />
    </Routes></MemoryRouter>);

    expect(screen.getByText(/Renews/i)).toBeInTheDocument();
    expect(screen.queryByText(/Usage resets/i)).not.toBeInTheDocument();
  });
});
