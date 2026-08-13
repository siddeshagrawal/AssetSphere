/**
 * Auth types — mirrors of backend DTOs.
 *
 * Sources:
 *   AuthenticationResponse.java
 *   CurrentUserResponse.java
 *   RegistrationResponse.java
 *   UserResponse.java
 *   WorkspaceSummary.java  (imported from workspace types)
 */

import type { WorkspaceSummary } from "./workspace";

// ── Requests ────────────────────────────────────────────────────────────────

export interface RegisterRequest {
  email: string;
  /** Backend: min 12, max 72 chars; requires uppercase, lowercase, and digit */
  password: string;
  /** Backend: max 120 chars */
  displayName: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface LogoutRequest {
  refreshToken: string;
}

// ── Responses ───────────────────────────────────────────────────────────────

/**
 * Returned by POST /api/v1/auth/login and POST /api/v1/auth/refresh.
 */
export interface AuthenticationResponse {
  tokenType: string;
  accessToken: string;
  accessTokenExpiresInSeconds: number;
  refreshToken: string;
  refreshTokenExpiresInSeconds: number;
}

/**
 * Returned by GET /api/v1/auth/me
 */
export interface CurrentUserResponse {
  user: UserResponse;
  workspaces: WorkspaceSummary[];
}

/**
 * Returned by POST /api/v1/auth/register
 */
export interface RegistrationResponse {
  user: UserResponse;
  defaultWorkspace: WorkspaceSummary;
}

export interface UserResponse {
  id: string;
  email: string;
  displayName: string;
  status: string;
  emailVerified: boolean;
  lastLoginAt: string | null;
}
