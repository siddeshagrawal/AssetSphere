import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { ArrowLeft, CheckCircle2, Loader2, ShieldCheck } from "lucide-react";
import { Link, useLocation, useParams } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useCreateLocalPayment, useCreateProCheckout, useLocalPaymentStatus, usePaymentCapabilities, useWorkspaceBilling } from "@/features/billing/hooks";
import type { CheckoutResponse, LocalPaymentMethod, LocalPaymentResponse } from "@/types/billing";

type CheckoutLocationState = { order?: CheckoutResponse };
type CardFieldsState = { pan: string; cvv: string; expiryMonth: string; expiryYear: string; cardHolderName: string };
const terminalStatuses = new Set(["CAPTURED", "SETTLED", "FAILED", "CANCELLED", "AUTH_EXPIRED"]);

export function LocalCheckoutPage() {
  const { workspaceId = "" } = useParams<{ workspaceId: string }>();
  const initialOrder = (useLocation().state as CheckoutLocationState | null)?.order;
  const requestedOrder = useRef(false);
  const capabilities = usePaymentCapabilities();
  const checkout = useCreateProCheckout(workspaceId);
  const createPayment = useCreateLocalPayment(workspaceId);
  const [method, setMethod] = useState<LocalPaymentMethod>("UPI");
  const [detail, setDetail] = useState("demo@bank");
  const [card, setCard] = useState({ pan: "4111111111111111", cvv: "123", expiryMonth: "12",
    expiryYear: String(new Date().getFullYear() + 1), cardHolderName: "Demo User" });
  const [pollingExpired, setPollingExpired] = useState(false);
  const order = initialOrder ?? checkout.data;
  const createdPayment = createPayment.data;
  const activePaymentId = createdPayment?.paymentId ?? order?.paymentId ?? undefined;
  const shouldPoll = Boolean(activePaymentId) && !pollingExpired
    && !terminalStatuses.has(createdPayment?.providerPaymentStatus ?? "");
  const status = useLocalPaymentStatus(workspaceId, order?.orderId, activePaymentId, shouldPoll);
  const payment: LocalPaymentResponse | undefined = status.data ?? createdPayment;
  const providerStatus = payment?.providerPaymentStatus;
  const captured = providerStatus === "CAPTURED" || providerStatus === "SETTLED";
  const unsuccessful = providerStatus === "FAILED" || providerStatus === "CANCELLED"
    || providerStatus === "AUTH_EXPIRED";
  const billing = useWorkspaceBilling(workspaceId, captured);

  useEffect(() => {
    if (initialOrder || checkout.data || requestedOrder.current || capabilities.isLoading) return;
    if (capabilities.data?.provider === "RAZORPAY_LOCAL"
        && !capabilities.data.supportsHostedCheckout && capabilities.data.supportsOrderCreation) {
      requestedOrder.current = true;
      checkout.mutate();
    }
  }, [capabilities.data, capabilities.isLoading, checkout, initialOrder]);

  useEffect(() => {
    if (!activePaymentId || terminalStatuses.has(providerStatus ?? "")) return;
    setPollingExpired(false);
    const timeout = window.setTimeout(() => setPollingExpired(true), 65_000);
    return () => window.clearTimeout(timeout);
  }, [activePaymentId, providerStatus]);

  useEffect(() => {
    if (method === "UPI") setDetail("demo@bank");
    if (method === "NETBANKING") setDetail("DEMO_BANK");
    if (method === "WALLET") setDetail("DEMO_WALLET");
  }, [method]);

  const amount = useMemo(() => order
    ? new Intl.NumberFormat("en-IN", { style: "currency", currency: order.currency }).format(order.amountMinor / 100)
    : "", [order]);
  const paymentMethods: LocalPaymentMethod[] = capabilities.data?.localCardEnabled
    ? ["CARD", "UPI", "NETBANKING", "WALLET"] : ["UPI", "NETBANKING", "WALLET"];
  const cardReady = /^\d{13,19}$/.test(card.pan) && /^\d{3,4}$/.test(card.cvv)
    && Number(card.expiryMonth) >= 1 && Number(card.expiryMonth) <= 12
    && Number(card.expiryYear) >= new Date().getFullYear() && card.cardHolderName.trim().length >= 3;

  async function submitPayment() {
    if (!order) return;
    try {
      if (method === "CARD") await createPayment.mutateAsync({ orderId: order.orderId, method,
        pan: card.pan.replace(/\s/g, ""), cvv: card.cvv, expiryMonth: Number(card.expiryMonth),
        expiryYear: Number(card.expiryYear), cardHolderName: card.cardHolderName });
      if (method === "UPI") await createPayment.mutateAsync({ orderId: order.orderId, method, vpa: detail });
      if (method === "NETBANKING") await createPayment.mutateAsync({ orderId: order.orderId, method, bank: detail });
      if (method === "WALLET") await createPayment.mutateAsync({ orderId: order.orderId, method, walletCode: detail });
    } catch {
      return;
    }
  }

  if (capabilities.data && (capabilities.data.provider !== "RAZORPAY_LOCAL"
      || capabilities.data.supportsHostedCheckout || !capabilities.data.supportsOrderCreation)) {
    return <CheckoutShell workspaceId={workspaceId}><p className="text-sm text-destructive">Local checkout is not available for the configured payment provider.</p></CheckoutShell>;
  }

  return <CheckoutShell workspaceId={workspaceId}>
    {!order && !checkout.error && <div className="flex items-center gap-3 py-12 text-sm text-muted-foreground"><Loader2 className="h-5 w-5 animate-spin" />Preparing your secure checkout…</div>}
    {checkout.error && <p className="text-sm text-destructive" role="alert">{checkout.error.message}</p>}
    {order && <>
      <div className="flex flex-col justify-between gap-4 border-b border-border pb-6 sm:flex-row sm:items-end"><div><p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">AssetSphere PRO</p><h1 className="mt-2 text-3xl font-semibold tracking-tight">Complete your upgrade</h1><p className="mt-2 text-sm text-muted-foreground">Payment state remains authoritative in Local Razorpay.</p></div><p className="text-3xl font-semibold">{amount}</p></div>
      <dl className="grid gap-3 border-b border-border py-5 text-sm sm:grid-cols-2"><div><dt className="text-muted-foreground">Provider</dt><dd className="mt-1 font-medium">Local Razorpay</dd></div><div><dt className="text-muted-foreground">Provider order</dt><dd className="mt-1 break-all font-mono text-xs">{order.orderId}</dd></div></dl>
      {!activePaymentId && <div className="pt-6"><Label>Payment method</Label><div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-4">{paymentMethods.map((value) => <Button key={value} type="button" variant={method === value ? "default" : "outline"} onClick={() => setMethod(value)}>{value === "NETBANKING" ? "Netbanking" : value[0] + value.slice(1).toLowerCase()}</Button>)}</div>{method === "CARD" ? <CardFields card={card} onChange={setCard} /> : <div className="mt-5"><Label htmlFor="payment-detail">{method === "UPI" ? "VPA" : method === "NETBANKING" ? "Bank code" : "Wallet code"}</Label><Input id="payment-detail" className="mt-2" value={detail} onChange={(event) => setDetail(event.target.value)} autoComplete="off" /><DemoPresets method={method} onSelect={setDetail} /></div>}<Button className="mt-6 w-full" size="lg" disabled={createPayment.isPending || (method === "CARD" ? !cardReady : !detail.trim())} onClick={() => void submitPayment()}>{createPayment.isPending && <Loader2 className="h-4 w-4 animate-spin" />}{createPayment.isPending ? "Creating payment" : `Pay ${amount}`}</Button>{createPayment.error && <p className="mt-3 text-sm text-destructive" role="alert">{createPayment.error.message}</p>}</div>}
      {payment && <div className="pt-6" role="status"><div className="flex items-center gap-3">{captured ? <CheckCircle2 className="h-6 w-6 text-emerald-600" /> : unsuccessful ? <span className="h-3 w-3 rounded-full bg-destructive" /> : <Loader2 className="h-5 w-5 animate-spin text-primary" />}<div><p className="font-semibold">{providerStatus}</p><p className="text-sm text-muted-foreground">Payment {payment.paymentId}</p>{payment.method === "CARD" && <p className="text-xs text-muted-foreground">Card •••• {card.pan.slice(-4)}</p>}</div></div>{captured && <div className="mt-5 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-950"><p className="font-medium">Payment captured by Local Razorpay.</p><p className="mt-1">{capabilities.data?.localPollConfirmationEnabled ? "Confirming subscription with the provider..." : "Waiting for verified provider confirmation before activating PRO."}</p></div>}{unsuccessful && <p className="mt-4 text-sm text-destructive">This provider payment did not complete. Your workspace remains on its current plan.</p>}{status.error && <p className="mt-4 text-sm text-destructive">{status.error.message}</p>}{pollingExpired && !terminalStatuses.has(providerStatus ?? "") && <p className="mt-4 text-sm text-muted-foreground">Provider confirmation is taking longer than expected. You can return to Billing and check again later.</p>}{billing.data?.plan === "PRO" && <div className="mt-5 rounded-lg border border-primary/20 bg-primary/5 p-4 text-sm font-medium text-primary">Verified provider confirmation received. AssetSphere PRO is active.</div>}</div>}
    </>}
  </CheckoutShell>;
}

function CardFields({ card, onChange }: { card: CardFieldsState; onChange: (value: CardFieldsState) => void }) {
  const field = (name: keyof typeof card, value: string) => onChange({ ...card, [name]: value });
  return <div className="mt-5 space-y-4"><div><Label htmlFor="card-number">Card number</Label><Input id="card-number" value={card.pan} inputMode="numeric" autoComplete="cc-number" maxLength={19} onChange={(event) => field("pan", event.target.value.replace(/\D/g, ""))} /></div><div><Label htmlFor="card-holder">Cardholder name</Label><Input id="card-holder" value={card.cardHolderName} autoComplete="cc-name" onChange={(event) => field("cardHolderName", event.target.value)} /></div><div className="grid gap-3 sm:grid-cols-3"><div><Label htmlFor="expiry-month">Month</Label><Input id="expiry-month" value={card.expiryMonth} inputMode="numeric" maxLength={2} onChange={(event) => field("expiryMonth", event.target.value.replace(/\D/g, ""))} /></div><div><Label htmlFor="expiry-year">Year</Label><Input id="expiry-year" value={card.expiryYear} inputMode="numeric" maxLength={4} onChange={(event) => field("expiryYear", event.target.value.replace(/\D/g, ""))} /></div><div><Label htmlFor="card-cvv">CVV</Label><Input id="card-cvv" type="password" value={card.cvv} inputMode="numeric" autoComplete="cc-csc" maxLength={4} onChange={(event) => field("cvv", event.target.value.replace(/\D/g, ""))} /></div></div><div className="rounded-lg border border-border bg-muted/30 p-3 text-xs text-muted-foreground"><p>Normal-flow card: 4111111111111111. Final simulator outcome is probability-based.</p><button type="button" className="mt-2 font-medium text-destructive underline-offset-2 hover:underline" onClick={() => field("pan", "4000000000000002")}>Use deterministic declined card</button></div></div>;
}

function DemoPresets({ method, onSelect }: { method: Exclude<LocalPaymentMethod, "CARD">; onSelect: (value: string) => void }) {
  const failure = method === "UPI" ? "fail@okaxis" : method === "NETBANKING" ? "BANK_CODE_FAIL" : "WALLET_CODE_FAIL";
  return <div className="mt-3 rounded-lg border border-border bg-muted/30 p-3 text-xs text-muted-foreground"><p>Normal values enter the probability-based simulator.</p><button type="button" className="mt-2 font-medium text-destructive underline-offset-2 hover:underline" onClick={() => onSelect(failure)}>Use deterministic failure: {failure}</button></div>;
}

function CheckoutShell({ workspaceId, children }: { workspaceId: string; children: ReactNode }) {
  return <div className="p-6"><div className="mx-auto max-w-2xl"><Link to={`/workspaces/${workspaceId}/billing`} className="mb-5 inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="h-4 w-4" />Back to billing</Link><section className="rounded-2xl border border-border bg-card p-6 shadow-sm sm:p-8"><div className="mb-6 flex items-center gap-2 text-xs font-medium text-muted-foreground"><ShieldCheck className="h-4 w-4 text-primary" />Secure local demo checkout</div>{children}</section></div></div>;
}
