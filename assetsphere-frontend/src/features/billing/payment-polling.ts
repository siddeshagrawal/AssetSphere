import type { BillingResponse } from "@/types/billing";

const terminalPaymentStatuses = new Set(["CAPTURED", "SETTLED", "FAILED", "CANCELLED", "AUTH_EXPIRED"]);
const stripeCheckoutVerificationUpdates = 10;

export function localPaymentPollingInterval(status: string | undefined, completedPolls: number): number | false {
  if (status && terminalPaymentStatuses.has(status)) return false;
  return completedPolls <= 1 ? 5_000 : 20_000;
}

export function workspaceBillingPollingInterval(
  billing: BillingResponse | undefined,
  completedUpdates: number,
  verifyStripeCheckout: boolean,
): number | false {
  if (billing?.latestPaymentStatus === "CREATED" || billing?.latestPaymentStatus === "ORDER_CREATED") {
    return 3_000;
  }
  if (verifyStripeCheckout
      && billing?.plan === "PRO"
      && billing.paymentProvider === "STRIPE"
      && billing.latestPaymentStatus === "PAID"
      && completedUpdates < stripeCheckoutVerificationUpdates) {
    return 3_000;
  }
  return false;
}
