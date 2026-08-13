/**
 * Central Axios instance for AssetSphere.
 *
 * Responsibilities:
 *   1. Inject Authorization header from in-memory access token.
 *   2. Parse typed ErrorResponse → ApiError for all error status codes.
 *   3. Coordinate single-flight token refresh on 401, then retry the
 *      original request exactly once.
 *
 * Dependency order (no circular imports):
 *   token-store  ← pure storage, no imports from this module
 *   api-client   ← imports token-store only
 *   *.api.ts     ← imports api-client
 *   features/*   ← imports *.api.ts
 *
 * The auth session context is NOT imported here. Instead, when a refresh
 * fails the client calls the `onSessionExpired` callback registered at
 * app startup via `registerSessionExpiredCallback`.
 */

import axios, {
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from "axios";
import type { ApiResponse, ErrorResponse } from "@/types/api";
import { ApiError } from "@/types/api";
import {
  getAccessToken,
  getRefreshToken,
  setAccessToken,
  setRefreshToken,
  clearAllTokens,
} from "./token-store";

// ── Session-expired callback ─────────────────────────────────────────────────

type SessionExpiredCallback = () => void;
let _onSessionExpired: SessionExpiredCallback | null = null;

/**
 * Register a callback that fires when a token refresh fails and the session
 * must be cleared. Called once during app bootstrap (AuthProvider).
 */
export function registerSessionExpiredCallback(cb: SessionExpiredCallback): void {
  _onSessionExpired = cb;
}

// ── Single-flight refresh coordinator ───────────────────────────────────────

/**
 * At most one in-flight refresh at a time.
 * Concurrent 401 responses share this promise and await the same result.
 */
let _refreshPromise: Promise<string> | null = null;

async function performRefresh(): Promise<string> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    throw new Error("No refresh token available");
  }

  // Use a bare axios call — NOT the intercepted instance — to avoid
  // triggering our own 401 interceptor recursively.
  interface RefreshPayload {
    tokenType: string;
    accessToken: string;
    accessTokenExpiresInSeconds: number;
    refreshToken: string;
    refreshTokenExpiresInSeconds: number;
  }
  const response = await axios.post<ApiResponse<RefreshPayload>>(
    `${import.meta.env.VITE_API_BASE_URL ?? ""}/api/v1/auth/refresh`,
    { refreshToken },
    { headers: { "Content-Type": "application/json" } }
  );

  const { accessToken, refreshToken: newRefreshToken } = response.data.data;
  setAccessToken(accessToken);
  setRefreshToken(newRefreshToken);
  return accessToken;
}

/**
 * Ensures only one refresh runs at a time.
 * All concurrent callers await the same promise.
 */
export function ensureSingleFlightRefresh(): Promise<string> {
  if (_refreshPromise === null) {
    _refreshPromise = performRefresh().finally(() => {
      _refreshPromise = null;
    });
  }
  return _refreshPromise;
}

// ── Axios instance ───────────────────────────────────────────────────────────

export const apiClient: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? "",
  headers: { "Content-Type": "application/json" },
  timeout: 30_000,
});

// ── Request interceptor: attach access token ─────────────────────────────────

apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig): InternalAxiosRequestConfig => {
    const token = getAccessToken();
    if (token) {
      config.headers.set("Authorization", `Bearer ${token}`);
    }
    return config;
  }
);

// ── Response interceptor: error parsing + 401 → refresh → retry ─────────────

// Flag stored on the config to prevent retrying more than once.
const RETRY_FLAG = "__as_retried__";

apiClient.interceptors.response.use(
  // Success: unwrap ApiResponse<T> transparently
  (response: AxiosResponse) => response,

  async (error: unknown) => {
    if (!axios.isAxiosError(error)) {
      return Promise.reject(new ApiError({
        code: "CLIENT_ERROR",
        message: error instanceof Error ? error.message : "An unexpected error occurred",
        status: 0,
        timestamp: new Date().toISOString(),
        correlationId: null,
        violations: [],
      }));
    }

    const originalConfig = error.config as (AxiosRequestConfig & { [RETRY_FLAG]?: boolean }) | undefined;
    const status = error.response?.status;

    // ── 401 → attempt token refresh ──────────────────────────────────────────
    if (
      status === 401 &&
      originalConfig &&
      !originalConfig[RETRY_FLAG] &&
      // Never retry the refresh or auth endpoints themselves
      !originalConfig.url?.includes("/api/v1/auth/refresh") &&
      !originalConfig.url?.includes("/api/v1/auth/login") &&
      !originalConfig.url?.includes("/api/v1/auth/register") &&
      !originalConfig.url?.includes("/api/v1/auth/oauth/exchange")
    ) {
      originalConfig[RETRY_FLAG] = true;

      try {
        await ensureSingleFlightRefresh();
        // Retry the original request once with the new token
        return apiClient(originalConfig);
      } catch {
        clearAllTokens();
        _onSessionExpired?.();
        return Promise.reject(buildApiError(error));
      }
    }

    // ── All other errors: parse into typed ApiError ──────────────────────────
    return Promise.reject(buildApiError(error));
  }
);

// ── Error builder ────────────────────────────────────────────────────────────

function buildApiError(error: unknown): ApiError {
  if (!axios.isAxiosError(error)) {
    return new ApiError({
      code: "CLIENT_ERROR",
      message: error instanceof Error ? error.message : "An unexpected error occurred",
      status: 0,
      timestamp: new Date().toISOString(),
      correlationId: null,
      violations: [],
    });
  }

  const retryAfter = error.response?.headers?.["retry-after"];
  const retryAfterSeconds = retryAfter ? parseInt(retryAfter, 10) : null;

  // Try to parse the structured ErrorResponse body
  const data = error.response?.data as Partial<ErrorResponse> | undefined;
  if (data?.code && data?.message && typeof data.status === "number") {
    return new ApiError(
      {
        code: data.code,
        message: data.message,
        status: data.status,
        timestamp: data.timestamp ?? new Date().toISOString(),
        correlationId: data.correlationId ?? null,
        violations: data.violations ?? [],
      },
      isNaN(retryAfterSeconds ?? NaN) ? null : retryAfterSeconds
    );
  }

  // Fallback for non-structured errors (e.g. network timeouts, CORS)
  return new ApiError(
    {
      code: "REQUEST_FAILED",
      message: error.message || "Request failed",
      status: error.response?.status ?? 0,
      timestamp: new Date().toISOString(),
      correlationId: null,
      violations: [],
    },
    isNaN(retryAfterSeconds ?? NaN) ? null : retryAfterSeconds
  );
}

// ── Typed response helper ────────────────────────────────────────────────────

/**
 * Unwraps ApiResponse<T>.data from a successful Axios response.
 * All api/*.ts functions use this helper so callers receive T directly.
 */
export function unwrap<T>(response: AxiosResponse<ApiResponse<T>>): T {
  return response.data.data;
}
