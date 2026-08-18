import { useState } from "react";
import { Check, CreditCard, Loader2 } from "lucide-react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { ErrorDisplay } from "@/components/shared/ErrorDisplay";
import { PageHeader } from "@/components/shared/PageHeader";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useAuth } from "@/features/auth/AuthProvider";
import { useCancelSubscription, useCreateProCheckout, usePaymentCapabilities, usePlans, useWorkspaceBilling } from "@/features/billing/hooks";
import { formatBackendDate, formatBytes } from "@/lib/utils";
import type { CheckoutResponse } from "@/types/billing";

export function BillingPage() {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { session } = useAuth();
  const workspaceRole = session.status === "AUTHENTICATED"
    ? session.workspaces.find((workspace) => workspace.id === workspaceId)?.role
    : undefined;
  const isOwner = workspaceRole === "OWNER";
  const stripeCheckoutOutcome = searchParams.get("checkout");
  const [localOrder, setLocalOrder] = useState<CheckoutResponse | null>(null);
  const [checkoutMessage, setCheckoutMessage] = useState<string | null>(null);
  const [checkoutError, setCheckoutError] = useState<string | null>(null);
  const billing = useWorkspaceBilling(isOwner ? workspaceId : undefined, stripeCheckoutOutcome === "success");
  const capabilities = usePaymentCapabilities();
  const plans = usePlans();
  const checkout = useCreateProCheckout(workspaceId ?? "");
  const cancelSubscription = useCancelSubscription(workspaceId ?? "");
  if (!workspaceId || session.status !== "AUTHENTICATED") return null;

  if (!isOwner) {
    return <div className="p-6"><div className="mx-auto max-w-5xl">
      <PageHeader title="Billing & plan" description="Workspace subscription and usage management." />
      <section className="rounded-xl border border-border bg-card p-6">
        <p className="text-xs font-semibold uppercase tracking-wider text-primary">Owner-managed</p>
        <h2 className="mt-2 text-xl font-semibold">Billing access is restricted</h2>
        <p className="mt-2 max-w-2xl text-sm text-muted-foreground">Only the workspace owner can view authoritative usage, start an upgrade, or manage the subscription. Your workspace access and available features remain governed by the current plan.</p>
      </section>
    </div></div>;
  }

  const paymentPending = billing.data?.latestPaymentStatus === "CREATED"
    || billing.data?.latestPaymentStatus === "ORDER_CREATED";
  async function startCheckout() {
    setCheckoutError(null);
    setCheckoutMessage(null);
    setLocalOrder(null);
    try {
      const order = await checkout.mutateAsync();
      if (order.supportsHostedCheckout) {
        if (!order.checkoutUrl) throw new Error("Hosted checkout URL was not returned");
        window.location.assign(order.checkoutUrl);
        return;
      }
      if (capabilities.data?.provider !== "RAZORPAY_LOCAL" || capabilities.data.supportsHostedCheckout
          || !capabilities.data.supportsOrderCreation) {
        throw new Error("Local payment checkout is unavailable");
      }
      setLocalOrder(order);
      setCheckoutMessage("Opening secure local checkout…");
      navigate(`/workspaces/${workspaceId}/billing/checkout/local`, { state: { order } });
    } catch (error) {
      setCheckoutError(error instanceof Error ? error.message : "Unable to create payment order");
    }
  }

  return <div className="p-6"><div className="mx-auto max-w-5xl">
    <PageHeader title="Billing & plan" description="Workspace entitlements and authoritative current-period usage." />
    {billing.isLoading && <div className="grid gap-4 md:grid-cols-2"><Skeleton className="h-52" /><Skeleton className="h-52" /></div>}
    {billing.isError && <ErrorDisplay error={billing.error} onRetry={() => billing.refetch()} />}
    {billing.data && <>
      <section className="rounded-xl border border-border bg-card p-6">
        <div className="flex flex-col justify-between gap-5 sm:flex-row sm:items-center">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wider text-primary">Current plan</p>
            <h2 className="mt-2 text-3xl font-semibold">{billing.data.plan}</h2>
            {billing.data.plan === "FREE" && <p className="mt-2 text-sm text-muted-foreground">Status {billing.data.subscriptionStatus} · Usage resets {formatBackendDate(billing.data.periodEnd)}</p>}
            {billing.data.plan === "PRO" && billing.data.paymentProvider === "RAZORPAY_LOCAL" && <p className="mt-2 text-sm font-medium">PRO active · Valid until {formatBackendDate(billing.data.periodEnd)} · Manual renewal</p>}
            {billing.data.plan === "PRO" && billing.data.paymentProvider === "STRIPE" && billing.data.cancelAtPeriodEnd && <p className="mt-2 text-sm font-medium text-amber-700">Cancellation scheduled · Access remains active until {formatBackendDate(billing.data.periodEnd)}</p>}
            {billing.data.plan === "PRO" && billing.data.paymentProvider === "STRIPE" && !billing.data.cancelAtPeriodEnd && <p className="mt-2 text-sm font-medium">Status {billing.data.subscriptionStatus} · {billing.data.autoRenew ? "Renews" : "Current period ends"} {formatBackendDate(billing.data.periodEnd)}</p>}
            {billing.data.plan === "FREE" && paymentPending && <p className="mt-2 text-sm font-medium text-amber-600">A payment order is awaiting verified provider confirmation.</p>}
            {billing.data.plan === "FREE" && billing.data.latestPaymentStatus === "FAILED" && <p className="mt-2 text-sm text-destructive">The last payment was not completed. You can try again.</p>}
            {billing.data.plan === "FREE" && billing.data.latestPaymentStatus === "CANCELED" && <p className="mt-2 text-sm text-muted-foreground">The last payment order was canceled.</p>}
            {stripeCheckoutOutcome === "success" && <p className="mt-2 text-sm text-muted-foreground" role="status">Checkout completed. Subscription status updates after verified Stripe confirmation.</p>}
            {stripeCheckoutOutcome === "cancel" && <p className="mt-2 text-sm text-muted-foreground" role="status">Checkout was canceled. Your current plan remains unchanged.</p>}
            {checkoutMessage && <p className="mt-2 max-w-xl text-sm text-muted-foreground" role="status">{checkoutMessage}</p>}
            {localOrder && <div className="mt-3 rounded-md border border-border bg-muted/30 px-3 py-2 text-xs"><p><span className="text-muted-foreground">Provider order</span> <span className="font-mono">{localOrder.orderId}</span></p><p className="mt-1"><span className="text-muted-foreground">Order state</span> {localOrder.providerOrderStatus ?? localOrder.paymentStatus}</p></div>}
            {checkoutError && <p className="mt-2 text-sm text-destructive" role="alert">{checkoutError}</p>}
            {cancelSubscription.error && <p className="mt-2 text-sm text-destructive" role="alert">{cancelSubscription.error.message}</p>}
            {capabilities.isError && <p className="mt-2 text-sm text-destructive">The selected payment provider is unavailable.</p>}
          </div>
          {billing.data.plan === "FREE" ? <Button disabled={checkout.isPending || !capabilities.data?.supportsOrderCreation} onClick={() => void startCheckout()}>{checkout.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <CreditCard className="h-4 w-4" />}Upgrade to PRO</Button>
            : billing.data.paymentProvider === "STRIPE" && billing.data.autoRenew ? <Button variant="outline" disabled={cancelSubscription.isPending} onClick={() => cancelSubscription.mutate()}>{cancelSubscription.isPending && <Loader2 className="h-4 w-4 animate-spin" />}Cancel at period end</Button>
              : <Button disabled><Check className="h-4 w-4" />{billing.data.plan} active</Button>}
        </div>
      </section>
      <section className="mt-6 grid gap-4 md:grid-cols-2"><Usage label="Assets" used={billing.data.usage.assets} limit={billing.data.entitlements.maxAssets} /><Usage label="Storage" used={billing.data.usage.storageBytes} limit={billing.data.entitlements.maxStorageBytes} format={formatBytes} /><Usage label="AI insights" used={billing.data.usage.aiInsights} limit={billing.data.entitlements.monthlyAiInsights} /><Usage label="Ask AssetSphere" used={billing.data.usage.askRequests} limit={billing.data.entitlements.monthlyAskRequests} /><Usage label="Evolution comparisons" used={billing.data.usage.evolutionComparisons} limit={billing.data.entitlements.monthlyEvolutionComparisons} /><Usage label="Knowledge Checks" used={billing.data.usage.quizGenerations} limit={billing.data.entitlements.monthlyQuizGenerations} /></section>
    </>}
    {plans.data && <section className="mt-8"><h2 className="text-lg font-semibold">Plan comparison</h2><div className="mt-4 grid gap-4 lg:grid-cols-3">{plans.data.map((plan) => <article key={plan.plan} className="rounded-xl border border-border bg-card p-6"><div className="flex items-center justify-between"><h3 className="text-xl font-semibold">{plan.plan}</h3>{plan.plan === billing.data?.plan && <span className="rounded bg-primary/10 px-2 py-1 text-[10px] font-semibold uppercase text-primary">Current</span>}</div>{plan.plan === "ENTERPRISE" && <p className="mt-2 text-sm font-medium text-primary">Custom pricing · Contact Sales</p>}<PlanFeatures plan={plan.entitlements} /></article>)}</div></section>}
  </div></div>;
}

function Usage({ label, used, limit, format = String }: { label: string; used: number; limit: number; format?: (value: number) => string }) {
  const percentage = Math.min(100, limit === 0 ? 100 : (used / limit) * 100);
  return <div className="rounded-lg border border-border bg-card p-5"><div className="flex justify-between text-sm"><span className="font-medium">{label}</span><span className="text-muted-foreground">{format(used)} / {format(limit)}</span></div><div className="mt-3 h-2 overflow-hidden rounded-full bg-muted"><div className="h-full rounded-full bg-primary transition-all" style={{ width: `${percentage}%` }} /></div></div>;
}

function PlanFeatures({ plan }: { plan: import("@/types/billing").PlanEntitlements }) {
  const values = [`${plan.maxAssets} assets`, `${plan.maxMembers} members`, `${formatBytes(plan.maxStorageBytes)} storage`, `${plan.monthlyAiInsights} AI insights / month`, `${plan.monthlyAskRequests} Ask requests / month`, `${plan.monthlyEvolutionComparisons} Evolution comparisons / month`, "Semantic + hybrid search", ...(plan.ocrEnabled ? ["OCR"] : []), ...(plan.videoTranscriptionEnabled ? ["Video transcription"] : [])];
  return <ul className="mt-5 space-y-2">{values.map((value) => <li key={value} className="flex gap-2 text-sm text-muted-foreground"><Check className="mt-0.5 h-4 w-4 shrink-0 text-primary" />{value}</li>)}</ul>;
}
