import { useEffect, useState } from "react";
import { Loader2 } from "lucide-react";
import { getAuthProviders } from "@/api/auth.api";
import { rememberReturnTo } from "@/features/auth/auth-redirect";

export function GoogleAuthButton({ returnTo }: { returnTo: string }) {
  const [enabled, setEnabled] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    getAuthProviders()
      .then((providers) => active && setEnabled(providers.google))
      .catch(() => active && setEnabled(false))
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, []);

  if (!loading && !enabled) return null;
  return <>
    <button
      type="button"
      disabled={loading}
      onClick={() => {
        rememberReturnTo(returnTo);
        window.location.assign(`${import.meta.env.VITE_API_BASE_URL ?? ""}/oauth2/authorization/google`);
      }}
      className="flex h-10 w-full items-center justify-center gap-2 rounded-md border border-input bg-background px-4 text-sm font-medium shadow-sm transition-colors hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-60"
    >
      {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <span className="font-semibold text-primary" aria-hidden="true">G</span>}
      Continue with Google
    </button>
    <div className="my-5 flex items-center gap-3" aria-label="or"><span className="h-px flex-1 bg-border" /><span className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">Or</span><span className="h-px flex-1 bg-border" /></div>
  </>;
}
