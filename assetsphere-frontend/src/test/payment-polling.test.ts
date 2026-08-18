import { describe, expect, it } from "vitest";
import { localPaymentPollingInterval, workspaceBillingPollingInterval } from "@/features/billing/payment-polling";
import type { BillingResponse } from "@/types/billing";

const stripeBilling = {
  plan: "PRO",
  paymentProvider: "STRIPE",
  latestPaymentStatus: "PAID",
} as BillingResponse;

describe("local payment polling", () => {
  it("backs off after the early provider checks", () => {
    expect(localPaymentPollingInterval("AUTHORIZING", 1)).toBe(5_000);
    expect(localPaymentPollingInterval("AUTHORIZED", 2)).toBe(20_000);
  });

  it.each(["CAPTURED", "SETTLED", "FAILED", "CANCELLED", "AUTH_EXPIRED"])(
    "stops polling at terminal status %s",
    (status) => expect(localPaymentPollingInterval(status, 3)).toBe(false),
  );
});

describe("workspace billing polling", () => {
  it("keeps pending checkout attempts refreshed", () => {
    expect(workspaceBillingPollingInterval(
      { ...stripeBilling, plan: "FREE", latestPaymentStatus: "ORDER_CREATED" },
      20,
      false,
    )).toBe(3_000);
  });

  it("briefly refreshes paid Stripe billing after a successful checkout redirect", () => {
    expect(workspaceBillingPollingInterval(stripeBilling, 1, true)).toBe(3_000);
    expect(workspaceBillingPollingInterval(stripeBilling, 10, true)).toBe(false);
  });

  it("does not poll an ordinary active Stripe billing view", () => {
    expect(workspaceBillingPollingInterval(stripeBilling, 1, false)).toBe(false);
  });
});
