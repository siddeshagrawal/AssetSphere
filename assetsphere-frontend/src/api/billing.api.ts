import type { AxiosResponse } from "axios";
import { apiClient, unwrap } from "@/lib/api-client";
import type { ApiResponse } from "@/types/api";
import type { BillingResponse, CheckoutResponse, LocalPaymentRequest, LocalPaymentResponse, PaymentCapabilitiesResponse, PlanResponse } from "@/types/billing";

export async function getWorkspaceBilling(workspaceId: string): Promise<BillingResponse> {
  const response: AxiosResponse<ApiResponse<BillingResponse>> = await apiClient.get(
    `/api/v1/workspaces/${workspaceId}/billing`
  );
  return unwrap(response);
}

export async function getPlans(): Promise<PlanResponse[]> {
  const response: AxiosResponse<ApiResponse<PlanResponse[]>> = await apiClient.get("/api/v1/billing/plans");
  return unwrap(response);
}

export async function getPaymentCapabilities(): Promise<PaymentCapabilitiesResponse> {
  const response: AxiosResponse<ApiResponse<PaymentCapabilitiesResponse>> = await apiClient.get(
    "/api/v1/billing/payment-capabilities"
  );
  return unwrap(response);
}

export async function createProCheckout(workspaceId: string, idempotencyKey: string): Promise<CheckoutResponse> {
  const response: AxiosResponse<ApiResponse<CheckoutResponse>> = await apiClient.post(
    `/api/v1/workspaces/${workspaceId}/billing/checkout`, {},
    { headers: { "Idempotency-Key": idempotencyKey } }
  );
  return unwrap(response);
}

export async function cancelSubscriptionAtPeriodEnd(workspaceId: string): Promise<void> {
  await apiClient.post(`/api/v1/workspaces/${workspaceId}/billing/cancel`, {});
}

export async function createLocalPayment(workspaceId: string, request: LocalPaymentRequest): Promise<LocalPaymentResponse> {
  const response: AxiosResponse<ApiResponse<LocalPaymentResponse>> = await apiClient.post(
    `/api/v1/workspaces/${workspaceId}/billing/local-payments`, request
  );
  return unwrap(response);
}

export async function getLocalPayment(workspaceId: string, orderId: string, paymentId: string): Promise<LocalPaymentResponse> {
  const response: AxiosResponse<ApiResponse<LocalPaymentResponse>> = await apiClient.get(
    `/api/v1/workspaces/${workspaceId}/billing/local-payments/${encodeURIComponent(orderId)}/${encodeURIComponent(paymentId)}`
  );
  return unwrap(response);
}
