/**
 * Auth API functions.
 *
 * All functions return the unwrapped data payload.
 * They throw ApiError on failure — never catch (e: any).
 */

import type { AxiosResponse } from "axios";
import { apiClient, unwrap } from "@/lib/api-client";
import type { ApiResponse } from "@/types/api";
import type {
  RegisterRequest,
  LoginRequest,
  LogoutRequest,
  RefreshRequest,
  AuthenticationResponse,
  CurrentUserResponse,
  RegistrationResponse,
} from "@/types/auth";

const BASE = "/api/v1/auth";

/**
 * POST /api/v1/auth/register
 *
 * Registration does NOT authenticate the user.
 * On success, the caller should navigate to /login.
 */
export async function register(
  request: RegisterRequest
): Promise<RegistrationResponse> {
  const response: AxiosResponse<ApiResponse<RegistrationResponse>> =
    await apiClient.post(`${BASE}/register`, request);
  return unwrap(response);
}

/**
 * POST /api/v1/auth/login
 *
 * Returns access + refresh tokens. The caller is responsible for storing them.
 */
export async function login(
  request: LoginRequest
): Promise<AuthenticationResponse> {
  const response: AxiosResponse<ApiResponse<AuthenticationResponse>> =
    await apiClient.post(`${BASE}/login`, request);
  return unwrap(response);
}

export async function exchangeOAuthCode(code: string): Promise<AuthenticationResponse> {
  const response: AxiosResponse<ApiResponse<AuthenticationResponse>> =
    await apiClient.post(`${BASE}/oauth/exchange`, { code });
  return unwrap(response);
}

export async function getAuthProviders(): Promise<{ google: boolean }> {
  const response: AxiosResponse<ApiResponse<{ google: boolean }>> =
    await apiClient.get(`${BASE}/providers`);
  return unwrap(response);
}

/**
 * POST /api/v1/auth/refresh
 *
 * Rotates both tokens. Never called directly by UI — the Axios interceptor
 * and auth bootstrap use this path. Exposed here only for the session
 * bootstrap flow in AuthProvider.
 */
export async function refresh(
  request: RefreshRequest
): Promise<AuthenticationResponse> {
  const response: AxiosResponse<ApiResponse<AuthenticationResponse>> =
    await apiClient.post(`${BASE}/refresh`, request);
  return unwrap(response);
}

/**
 * POST /api/v1/auth/logout
 *
 * Requires a valid access token (handled by the Axios interceptor).
 * Invalidates the refresh token server-side.
 */
export async function logout(request: LogoutRequest): Promise<void> {
  await apiClient.post(`${BASE}/logout`, request);
}

/**
 * GET /api/v1/auth/me
 *
 * Returns the current authenticated user and their workspace memberships.
 * Used during session bootstrap and after login.
 */
export async function me(): Promise<CurrentUserResponse> {
  const response: AxiosResponse<ApiResponse<CurrentUserResponse>> =
    await apiClient.get(`${BASE}/me`);
  return unwrap(response);
}
