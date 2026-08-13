import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Link, useLocation } from "react-router-dom";
import { useState } from "react";
import { Eye, EyeOff, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useRegister } from "@/features/auth/hooks";
import { ApiError } from "@/types/api";
import { AuthLayout } from "@/components/auth/AuthLayout";
import { GoogleAuthButton } from "@/components/auth/GoogleAuthButton";
import { returnToFromSearch } from "@/features/auth/auth-redirect";

/**
 * Password constraints from backend RegisterRequest.java:
 *   - min 12, max 72 characters
 *   - at least one uppercase letter
 *   - at least one lowercase letter
 *   - at least one digit
 */
const schema = z.object({
  email: z
    .string()
    .email("Enter a valid email address")
    .max(320, "Email must be 320 characters or fewer"),
  displayName: z
    .string()
    .min(1, "Display name is required")
    .max(120, "Display name must be 120 characters or fewer"),
  password: z
    .string()
    .min(12, "Password must be at least 12 characters")
    .max(72, "Password must be 72 characters or fewer")
    .regex(/[A-Z]/, "Password must contain at least one uppercase letter")
    .regex(/[a-z]/, "Password must contain at least one lowercase letter")
    .regex(/\d/, "Password must contain at least one digit"),
  confirmPassword: z.string().min(1, "Confirm your password"),
}).refine((values) => values.password === values.confirmPassword, {
  path: ["confirmPassword"],
  message: "Passwords do not match",
});

type FormValues = z.infer<typeof schema>;

export function RegisterPage() {
  const registerMutation = useRegister();
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmation, setShowConfirmation] = useState(false);
  const location = useLocation();
  const returnTo = returnToFromSearch(location.search);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
  });

  async function onSubmit(values: FormValues) {
    try {
      await registerMutation.mutateAsync({
        email: values.email,
        displayName: values.displayName,
        password: values.password,
      });
      // onSuccess in the hook handles navigation to /login
    } catch (err) {
      if (err instanceof ApiError && err.isValidation()) {
        const map = err.violationMap();
        (Object.entries(map) as [keyof FormValues, string][]).forEach(
          ([field, msg]) => setError(field, { message: msg })
        );
      }
    }
  }

  const apiError =
    registerMutation.error instanceof ApiError ? registerMutation.error : null;

  let topLevelError: string | null = null;
  if (apiError && !apiError.isValidation()) {
    if (apiError.status === 409) {
      topLevelError = "An account with this email already exists.";
    } else {
      topLevelError = apiError.message;
    }
  }

  return (
    <AuthLayout>
      <div className="mb-7"><p className="text-xs font-semibold uppercase tracking-wider text-primary">Get started</p><h1 className="mt-2 text-2xl font-semibold">Create your account</h1><p className="mt-2 text-sm text-muted-foreground">Build a secure workspace for your team’s knowledge.</p></div>

        {/* Form card */}
        <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <GoogleAuthButton returnTo={returnTo} />
          <form onSubmit={handleSubmit(onSubmit)} noValidate>
            <div className="space-y-4">
              {/* Email */}
              <div className="space-y-1.5">
                <Label htmlFor="reg-email">Email</Label>
                <Input
                  id="reg-email"
                  type="email"
                  autoComplete="email"
                  placeholder="you@example.com"
                  aria-describedby={errors.email ? "reg-email-error" : undefined}
                  aria-invalid={!!errors.email}
                  {...register("email")}
                />
                {errors.email && (
                  <p id="reg-email-error" className="text-xs text-destructive" role="alert">
                    {errors.email.message}
                  </p>
                )}
              </div>

              {/* Display name */}
              <div className="space-y-1.5">
                <Label htmlFor="reg-displayName">Display name</Label>
                <Input
                  id="reg-displayName"
                  type="text"
                  autoComplete="name"
                  placeholder="Your full name"
                  aria-describedby={errors.displayName ? "reg-name-error" : undefined}
                  aria-invalid={!!errors.displayName}
                  {...register("displayName")}
                />
                {errors.displayName && (
                  <p id="reg-name-error" className="text-xs text-destructive" role="alert">
                    {errors.displayName.message}
                  </p>
                )}
              </div>

              {/* Password */}
              <div className="space-y-1.5">
                <Label htmlFor="reg-password">Password</Label>
                <div className="relative"><Input id="reg-password" type={showPassword ? "text" : "password"} autoComplete="new-password" placeholder="Create a strong password" className="pr-10" aria-describedby="reg-password-hint reg-password-error" aria-invalid={!!errors.password} {...register("password")} /><button type="button" onClick={() => setShowPassword((value) => !value)} className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" aria-label={showPassword ? "Hide password" : "Show password"}>{showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}</button></div>
                <p id="reg-password-hint" className="text-xs text-muted-foreground">
                  At least 12 characters with one uppercase letter, one lowercase, and one digit.
                </p>
                {errors.password && (
                  <p id="reg-password-error" className="text-xs text-destructive" role="alert">
                    {errors.password.message}
                  </p>
                )}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="reg-confirm-password">Confirm password</Label>
                <div className="relative"><Input id="reg-confirm-password" type={showConfirmation ? "text" : "password"} autoComplete="new-password" placeholder="Repeat your password" className="pr-10" aria-describedby={errors.confirmPassword ? "reg-confirm-error" : undefined} aria-invalid={!!errors.confirmPassword} {...register("confirmPassword")} /><button type="button" onClick={() => setShowConfirmation((value) => !value)} className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" aria-label={showConfirmation ? "Hide confirmed password" : "Show confirmed password"}>{showConfirmation ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}</button></div>
                {errors.confirmPassword && <p id="reg-confirm-error" className="text-xs text-destructive" role="alert">{errors.confirmPassword.message}</p>}
              </div>

              {/* Top-level API error */}
              {topLevelError && (
                <div
                  className="rounded-md border border-destructive/30 bg-destructive/5 px-3 py-2"
                  role="alert"
                >
                  <p className="text-sm text-destructive">{topLevelError}</p>
                </div>
              )}

              <Button
                type="submit"
                className="w-full"
                disabled={isSubmitting || registerMutation.isPending}
              >
                {(isSubmitting || registerMutation.isPending) && (
                  <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
                )}
                Create account
              </Button>
            </div>
          </form>
        </div>

        {/* Login link */}
        <p className="mt-4 text-center text-sm text-muted-foreground">
          Already have an account?{" "}
          <Link
            to={`/login?returnTo=${encodeURIComponent(returnTo)}`}
            className="font-medium text-primary hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring rounded"
          >
            Sign in
          </Link>
        </p>
    </AuthLayout>
  );
}
