export type Plan = "FREE" | "PRO" | "ENTERPRISE";
export type SubscriptionStatus = "ACTIVE" | "PAST_DUE" | "CANCELED";
export type PaymentStatus = "CREATED" | "ORDER_CREATED" | "PAID" | "FAILED" | "CANCELED";
export type PaymentProvider = "STRIPE" | "RAZORPAY_LOCAL";

export interface PlanEntitlements {
  maxAssets: number;
  maxStorageBytes: number;
  maxMembers: number;
  monthlyAiInsights: number;
  monthlyAskRequests: number;
  monthlyEvolutionComparisons: number;
  monthlySemanticSearches: number;
  monthlyQuizGenerations: number;
  ocrEnabled: boolean;
  videoTranscriptionEnabled: boolean;
  fullAuditEnabled: boolean;
}

export interface BillingUsage {
  assets: number;
  storageBytes: number;
  aiInsights: number;
  askRequests: number;
  evolutionComparisons: number;
  quizGenerations: number;
}

export interface BillingResponse {
  plan: Plan;
  subscriptionStatus: SubscriptionStatus;
  entitlements: PlanEntitlements;
  usage: BillingUsage;
  remaining: BillingUsage;
  periodStart: string | number;
  periodEnd: string | number;
  latestPaymentStatus: PaymentStatus | null;
  paymentProvider: PaymentProvider | null;
  autoRenew: boolean;
  cancelAtPeriodEnd: boolean;
}

export interface PlanResponse {
  plan: Plan;
  entitlements: PlanEntitlements;
}

export interface CheckoutResponse {
  provider: PaymentProvider;
  keyId: string | null;
  orderId: string;
  checkoutUrl: string | null;
  paymentId?: string | null;
  supportsHostedCheckout: boolean;
  providerOrderStatus: string | null;
  amountMinor: number;
  currency: string;
  paymentStatus: PaymentStatus;
}

export interface PaymentCapabilitiesResponse {
  provider: PaymentProvider;
  supportsHostedCheckout: boolean;
  supportsOrderCreation: boolean;
  localPollConfirmationEnabled: boolean;
  localCardEnabled: boolean;
}

export type LocalPaymentMethod = "CARD" | "UPI" | "NETBANKING" | "WALLET";

export interface LocalPaymentRequest {
  orderId: string;
  method: LocalPaymentMethod;
  vpa?: string;
  bank?: string;
  walletCode?: string;
  pan?: string;
  cvv?: string;
  expiryMonth?: number;
  expiryYear?: number;
  cardHolderName?: string;
}

export interface LocalPaymentResponse {
  paymentId: string;
  orderId: string;
  providerPaymentStatus: string;
  method: LocalPaymentMethod;
  amountMinor: number;
  currency: string;
  createdAt: string;
}
