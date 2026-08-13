import { describe, expect, it } from "vitest";
import { localPaymentPollingInterval } from "@/features/billing/payment-polling";

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
