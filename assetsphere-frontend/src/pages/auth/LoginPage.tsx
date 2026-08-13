import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Link, useLocation } from "react-router-dom";
import { useState } from "react";
import { Eye, EyeOff, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useLogin } from "@/features/auth/hooks";
import { ApiError } from "@/types/api";
import { AuthLayout } from "@/components/auth/AuthLayout";
import { GoogleAuthButton } from "@/components/auth/GoogleAuthButton";
import { returnToFromSearch } from "@/features/auth/auth-redirect";

const schema = z.object({
  email: z.string().email("Enter a valid email address"),
  password: z.string().min(1, "Password is required"),
});

type FormValues = z.infer<typeof schema>;

export function LoginPage() {
  const loginMutation = useLogin();
  const [showPassword, setShowPassword] = useState(false);
  const location = useLocation();
  const returnTo = returnToFromSearch(location.search);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
  });

  async function onSubmit(values: FormValues) {
    await loginMutation.mutateAsync(values);
  }

  // Extract a clean error message from the mutation error
  const apiError =
    loginMutation.error instanceof ApiError ? loginMutation.error : null;

  let errorMessage: string | null = new URLSearchParams(location.search).get("error") === "oauth_failed"
    ? "Google sign-in could not be completed. Please try again."
    : null;
  if (apiError) {
    if (apiError.status === 401) {
      errorMessage = "Invalid email or password.";
    } else if (apiError.status === 423) {
      errorMessage = "Account is locked. Please try again later.";
    } else {
      errorMessage = apiError.message;
    }
  }

  return (
    <AuthLayout>
      <div className="mb-7"><p className="text-xs font-semibold uppercase tracking-wider text-primary">Welcome back</p><h1 className="mt-2 text-2xl font-semibold">Sign in to AssetSphere</h1><p className="mt-2 text-sm text-muted-foreground">Continue to your secure knowledge workspace.</p></div>

        {/* Form card */}
        <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <GoogleAuthButton returnTo={returnTo} />
          <form onSubmit={handleSubmit(onSubmit)} noValidate>
            <div className="space-y-4">
              {/* Email */}
              <div className="space-y-1.5">
                <Label htmlFor="login-email">Email</Label>
                <Input
                  id="login-email"
                  type="email"
                  autoComplete="email"
                  placeholder="you@example.com"
                  aria-describedby={errors.email ? "login-email-error" : undefined}
                  aria-invalid={!!errors.email}
                  {...register("email")}
                />
                {errors.email && (
                  <p id="login-email-error" className="text-xs text-destructive" role="alert">
                    {errors.email.message}
                  </p>
                )}
              </div>

              {/* Password */}
              <div className="space-y-1.5">
                <Label htmlFor="login-password">Password</Label>
                <div className="relative"><Input id="login-password" type={showPassword ? "text" : "password"} autoComplete="current-password" placeholder="Enter your password" className="pr-10" aria-describedby={errors.password ? "login-password-error" : undefined} aria-invalid={!!errors.password} {...register("password")} /><button type="button" onClick={() => setShowPassword((value) => !value)} className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" aria-label={showPassword ? "Hide password" : "Show password"}>{showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}</button></div>
                {errors.password && (
                  <p id="login-password-error" className="text-xs text-destructive" role="alert">
                    {errors.password.message}
                  </p>
                )}
              </div>

              {/* API-level error */}
              {errorMessage && (
                <div
                  className="rounded-md border border-destructive/30 bg-destructive/5 px-3 py-2"
                  role="alert"
                >
                  <p className="text-sm text-destructive">{errorMessage}</p>
                </div>
              )}

              <Button
                type="submit"
                className="w-full"
                disabled={isSubmitting || loginMutation.isPending}
              >
                {(isSubmitting || loginMutation.isPending) && (
                  <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
                )}
                Sign in
              </Button>
            </div>
          </form>
        </div>

        {/* Register link */}
        <p className="mt-4 text-center text-sm text-muted-foreground">
          Don&apos;t have an account?{" "}
          <Link
            to={`/register?returnTo=${encodeURIComponent(returnTo)}`}
            className="font-medium text-primary hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring rounded"
          >
            Create account
          </Link>
        </p>
    </AuthLayout>
  );
}
