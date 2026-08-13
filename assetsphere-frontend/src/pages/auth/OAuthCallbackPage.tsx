import { useEffect, useRef, useState } from "react";
import { Loader2 } from "lucide-react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { exchangeOAuthCode } from "@/api/auth.api";
import { useAuth } from "@/features/auth/AuthProvider";
import { consumeReturnTo } from "@/features/auth/auth-redirect";

export function OAuthCallbackPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { handleLoginSuccess } = useAuth();
  const started = useRef(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const code = params.get("code");
    if (started.current || !code) {
      if (!code) setError("Google sign-in did not return a valid login code.");
      return;
    }
    started.current = true;
    exchangeOAuthCode(code)
      .then(async (session) => {
        await handleLoginSuccess(session.accessToken, session.refreshToken);
        navigate(consumeReturnTo(), { replace: true });
      })
      .catch(() => setError("Google sign-in could not be completed. Please try again."));
  }, [handleLoginSuccess, navigate, params]);

  return <main className="flex min-h-screen items-center justify-center bg-muted/20 px-4"><div className="w-full max-w-sm rounded-xl border border-border bg-card p-7 text-center shadow-sm">{error ? <><h1 className="text-lg font-semibold">Sign-in failed</h1><p className="mt-2 text-sm text-destructive" role="alert">{error}</p><Link className="mt-5 inline-block text-sm font-medium text-primary hover:underline" to="/login">Return to sign in</Link></> : <><Loader2 className="mx-auto h-6 w-6 animate-spin text-primary" /><h1 className="mt-4 text-lg font-semibold">Completing secure sign-in</h1><p className="mt-1 text-sm text-muted-foreground">This one-time login code will be exchanged for your AssetSphere session.</p></>}</div></main>;
}
