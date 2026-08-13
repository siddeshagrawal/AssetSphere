const terminalPaymentStatuses = new Set(["CAPTURED", "SETTLED", "FAILED", "CANCELLED", "AUTH_EXPIRED"]);

export function localPaymentPollingInterval(status: string | undefined, completedPolls: number): number | false {
  if (status && terminalPaymentStatuses.has(status)) return false;
  return completedPolls <= 1 ? 5_000 : 20_000;
}
