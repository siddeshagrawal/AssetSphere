/**
 * Auth TanStack Query mutation hooks.
 *
 * Mutations do not use useQuery because auth operations are imperative
 * actions, not data-fetching. TanStack useMutation handles loading/error
 * state cleanly for forms.
 */

import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import { useLocation, useNavigate } from "react-router-dom";
import * as authApi from "@/api/auth.api";
import { useAuth } from "./AuthProvider";
import type { LoginRequest, RegisterRequest } from "@/types/auth";
import { ApiError } from "@/types/api";
import { consumeReturnTo, returnToFromSearch } from "./auth-redirect";

// ── Login ────────────────────────────────────────────────────────────────────

export function useLogin() {
  const { handleLoginSuccess } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  return useMutation({
    mutationFn: (request: LoginRequest) => authApi.login(request),
    onSuccess: async (data) => {
      await handleLoginSuccess(data.accessToken, data.refreshToken);
      navigate(consumeReturnTo(location.search));
    },
    onError: (error: Error) => {
      if (!(error instanceof ApiError)) {
        toast.error("Login failed. Please try again.");
      }
      // ApiError is surfaced directly in the form via mutation.error
    },
  });
}

// ── Register ─────────────────────────────────────────────────────────────────

export function useRegister() {
  const navigate = useNavigate();
  const location = useLocation();

  return useMutation({
    mutationFn: (request: RegisterRequest) => authApi.register(request),
    onSuccess: () => {
      toast.success("Account created — please sign in.");
      const returnTo = returnToFromSearch(location.search);
      navigate(`/login?returnTo=${encodeURIComponent(returnTo)}`);
    },
    onError: (error: Error) => {
      if (!(error instanceof ApiError)) {
        toast.error("Registration failed. Please try again.");
      }
    },
  });
}

// ── Logout ───────────────────────────────────────────────────────────────────

export function useLogout() {
  const { handleLogout } = useAuth();
  const navigate = useNavigate();

  return useMutation({
    mutationFn: () => handleLogout(),
    onSuccess: () => {
      navigate("/login");
    },
    onError: () => {
      // Logout cleaned up locally even on network error; still navigate
      navigate("/login");
    },
  });
}
