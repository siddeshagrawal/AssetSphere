import { useEffect } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { cancelSubscriptionAtPeriodEnd, createLocalPayment, createProCheckout, getLocalPayment, getPaymentCapabilities, getPlans, getWorkspaceBilling } from "@/api/billing.api";
import type { LocalPaymentRequest } from "@/types/billing";
import { createClientRequestId } from "@/lib/utils";
import { workspaceKeys } from "@/features/workspaces/hooks";
import { localPaymentPollingInterval, workspaceBillingPollingInterval } from "@/features/billing/payment-polling";

export const billingKeys = {
  plans: ["billing", "plans"] as const,
  paymentCapabilities: ["billing", "payment-capabilities"] as const,
  workspace: (workspaceId: string) => ["billing", "workspace", workspaceId] as const,
};

export function usePlans() {
  return useQuery({ queryKey: billingKeys.plans, queryFn: getPlans, staleTime: 10 * 60_000 });
}

export function usePaymentCapabilities() {
  return useQuery({
    queryKey: billingKeys.paymentCapabilities,
    queryFn: getPaymentCapabilities,
    staleTime: 10 * 60_000,
  });
}

export function useWorkspaceBilling(workspaceId: string | undefined, verifyPayment = false) {
  return useQuery({
    queryKey: billingKeys.workspace(workspaceId ?? ""),
    queryFn: () => getWorkspaceBilling(workspaceId!),
    enabled: Boolean(workspaceId),
    refetchInterval: (query) => workspaceBillingPollingInterval(
      query.state.data,
      query.state.dataUpdateCount,
      verifyPayment,
    ),
  });
}

export function useCreateProCheckout(workspaceId: string) {
  return useMutation({ mutationFn: () => createProCheckout(workspaceId, createClientRequestId()) });
}

export function useCancelSubscription(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => cancelSubscriptionAtPeriodEnd(workspaceId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: billingKeys.workspace(workspaceId) }),
  });
}

export function useCreateLocalPayment(workspaceId: string) {
  return useMutation({ mutationFn: (request: LocalPaymentRequest) => createLocalPayment(workspaceId, request) });
}

export function useLocalPaymentStatus(workspaceId: string, orderId: string | undefined,
                                      paymentId: string | undefined, enabled: boolean) {
  const queryClient = useQueryClient();
  const query = useQuery({
    queryKey: ["billing", "local-payment", workspaceId, orderId, paymentId],
    queryFn: () => getLocalPayment(workspaceId, orderId!, paymentId!),
    enabled: enabled && Boolean(workspaceId && orderId && paymentId),
    refetchInterval: (query) => {
      if (!enabled) return false;
      return localPaymentPollingInterval(query.state.data?.providerPaymentStatus, query.state.dataUpdateCount);
    },
  });
  useEffect(() => {
    const status = query.data?.providerPaymentStatus;
    if (!status || !["CAPTURED", "SETTLED", "FAILED", "CANCELLED", "AUTH_EXPIRED"].includes(status)) return;
    void queryClient.invalidateQueries({ queryKey: billingKeys.workspace(workspaceId) });
    void queryClient.invalidateQueries({ queryKey: workspaceKeys.all });
    void queryClient.invalidateQueries({ queryKey: billingKeys.paymentCapabilities });
  }, [query.data?.providerPaymentStatus, queryClient, workspaceId]);
  return query;
}
