import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { LocalCheckoutPage } from "@/pages/billing/LocalCheckoutPage";
import type { LocalPaymentResponse, PaymentCapabilitiesResponse } from "@/types/billing";

const mocks = vi.hoisted(() => ({
  capabilities: { provider: "RAZORPAY_LOCAL", supportsHostedCheckout: false, supportsOrderCreation: true,
    localPollConfirmationEnabled: false, localCardEnabled: true } as PaymentCapabilitiesResponse,
  payment: undefined as LocalPaymentResponse | undefined,
  mutateAsync: vi.fn(),
}));

vi.mock("@/features/billing/hooks", () => ({
  usePaymentCapabilities: () => ({ data: mocks.capabilities, isLoading: false }),
  useCreateProCheckout: () => ({ data: undefined, error: null, isPending: false, mutate: vi.fn() }),
  useCreateLocalPayment: () => ({ data: mocks.payment, error: null, isPending: false, mutateAsync: mocks.mutateAsync }),
  useLocalPaymentStatus: () => ({ data: undefined, error: null }),
  useWorkspaceBilling: () => ({ data: { plan: "FREE" } }),
}));

const order = {
  provider: "RAZORPAY_LOCAL" as const,
  keyId: null,
  orderId: "provider-order-1",
  checkoutUrl: null,
  supportsHostedCheckout: false,
  providerOrderStatus: "CREATED",
  amountMinor: 99_900,
  currency: "INR",
  paymentStatus: "ORDER_CREATED" as const,
};

function renderPage() {
  return render(<MemoryRouter initialEntries={[{
    pathname: "/workspaces/workspace-1/billing/checkout/local",
    state: { order },
  }]}><Routes><Route path="/workspaces/:workspaceId/billing/checkout/local" element={<LocalCheckoutPage />} /></Routes></MemoryRouter>);
}

describe("local checkout", () => {
  beforeEach(() => {
    mocks.capabilities = { provider: "RAZORPAY_LOCAL", supportsHostedCheckout: false, supportsOrderCreation: true,
      localPollConfirmationEnabled: false, localCardEnabled: true };
    mocks.payment = undefined;
    mocks.mutateAsync.mockReset();
  });

  it("supports demo methods without rendering card or secrets", async () => {
    renderPage();
    expect(screen.getByRole("button", { name: /upi/i })).toBeInTheDocument();
    expect(screen.getByText("Netbanking")).toBeInTheDocument();
    expect(screen.getByText("Wallet")).toBeInTheDocument();
    expect(screen.getByText("Card")).toBeInTheDocument();
    expect(screen.queryByText(/key secret|authorization/i)).not.toBeInTheDocument();

    fireEvent.click(screen.getByText("Wallet"));
    fireEvent.change(screen.getByLabelText("Wallet code"), { target: { value: "DEMO_WALLET" } });
    fireEvent.click(screen.getByRole("button", { name: /Pay/ }));

    await waitFor(() => expect(mocks.mutateAsync).toHaveBeenCalledWith({
      orderId: "provider-order-1", method: "WALLET", walletCode: "DEMO_WALLET",
    }));
  });

  it("submits card data only through the AssetSphere payment mutation", async () => {
    renderPage();
    fireEvent.click(screen.getByText("Card"));
    expect(screen.getByText(/deterministic declined card/i)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /Pay/ }));
    await waitFor(() => expect(mocks.mutateAsync).toHaveBeenCalledWith(expect.objectContaining({
      orderId: "provider-order-1", method: "CARD", pan: "4111111111111111", cvv: "123",
      cardHolderName: "Demo User",
    })));
  });

  it("disables card submission for invalid card fields", () => {
    renderPage();
    fireEvent.click(screen.getByText("Card"));
    fireEvent.change(screen.getByLabelText("Card number"), { target: { value: "123" } });
    expect(screen.getByRole("button", { name: /Pay/ })).toBeDisabled();
  });

  it("shows captured as awaiting verified confirmation", () => {
    mocks.payment = { paymentId: "payment-1", orderId: "provider-order-1", providerPaymentStatus: "CAPTURED",
      method: "UPI", amountMinor: 99_900, currency: "INR", createdAt: "2026-08-11T00:00:00Z" };
    renderPage();
    expect(screen.getByText("Payment captured by Local Razorpay.")).toBeInTheDocument();
    expect(screen.getByText("Waiting for verified provider confirmation before activating PRO.")).toBeInTheDocument();
  });

  it("shows provider confirmation progress when trusted local polling is enabled", () => {
    mocks.capabilities.localPollConfirmationEnabled = true;
    mocks.payment = { paymentId: "payment-1", orderId: "provider-order-1", providerPaymentStatus: "CAPTURED",
      method: "UPI", amountMinor: 99_900, currency: "INR", createdAt: "2026-08-11T00:00:00Z" };
    renderPage();
    expect(screen.getByText("Confirming subscription with the provider...")).toBeInTheDocument();
  });

  it("shows provider failure without claiming an upgrade", () => {
    mocks.payment = { paymentId: "payment-1", orderId: "provider-order-1", providerPaymentStatus: "FAILED",
      method: "UPI", amountMinor: 99_900, currency: "INR", createdAt: "2026-08-11T00:00:00Z" };
    renderPage();
    expect(screen.getByText(/provider payment did not complete/i)).toBeInTheDocument();
    expect(screen.queryByText(/PRO is active/i)).not.toBeInTheDocument();
  });

  it("does not expose local checkout in Stripe mode", () => {
    mocks.capabilities = { provider: "STRIPE", supportsHostedCheckout: true, supportsOrderCreation: true,
      localPollConfirmationEnabled: false, localCardEnabled: false };
    renderPage();
    expect(screen.getByText("Local checkout is not available for the configured payment provider.")).toBeInTheDocument();
  });
});
