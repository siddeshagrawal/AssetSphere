/**
 * AuthProvider — central auth session state.
 *
 * Session states:
 *   BOOTSTRAPPING  — app startup: attempting to restore session from
 *                    sessionStorage refresh token. Protected routes MUST
 *                    NOT render authenticated UI until this resolves.
 *   AUTHENTICATED  — access token in memory, user data loaded.
 *   UNAUTHENTICATED — no valid session; user must log in.
 *
 * Token storage contract (enforced here and in token-store.ts):
 *   Access token  → memory only (module variable in token-store.ts)
 *   Refresh token → sessionStorage only
 *   Neither token is ever written to localStorage or logged.
 */

import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useReducer,
} from "react";
import type { UserResponse } from "@/types/auth";
import type { WorkspaceSummary } from "@/types/workspace";
import {
  setAccessToken,
  setRefreshToken,
  clearAllTokens,
  getRefreshToken,
} from "@/lib/token-store";
import { ensureSingleFlightRefresh, registerSessionExpiredCallback } from "@/lib/api-client";
import { queryClient } from "@/lib/query-client";
import * as authApi from "@/api/auth.api";

// ── Types ────────────────────────────────────────────────────────────────────

export type SessionStatus = "BOOTSTRAPPING" | "AUTHENTICATED" | "UNAUTHENTICATED";

interface AuthenticatedSession {
  status: "AUTHENTICATED";
  user: UserResponse;
  workspaces: WorkspaceSummary[];
}

interface UnauthenticatedSession {
  status: "UNAUTHENTICATED";
  user: null;
  workspaces: null;
}

interface BootstrappingSession {
  status: "BOOTSTRAPPING";
  user: null;
  workspaces: null;
}

type SessionState =
  | BootstrappingSession
  | AuthenticatedSession
  | UnauthenticatedSession;

type SessionAction =
  | { type: "SET_AUTHENTICATED"; user: UserResponse; workspaces: WorkspaceSummary[] }
  | { type: "SET_UNAUTHENTICATED" }
  | { type: "UPDATE_WORKSPACES"; workspaces: WorkspaceSummary[] };

function sessionReducer(state: SessionState, action: SessionAction): SessionState {
  switch (action.type) {
    case "SET_AUTHENTICATED":
      return {
        status: "AUTHENTICATED",
        user: action.user,
        workspaces: action.workspaces,
      };
    case "SET_UNAUTHENTICATED":
      return { status: "UNAUTHENTICATED", user: null, workspaces: null };
    case "UPDATE_WORKSPACES":
      if (state.status !== "AUTHENTICATED") return state;
      return { ...state, workspaces: action.workspaces };
    default:
      return state;
  }
}

// ── Context ──────────────────────────────────────────────────────────────────

interface AuthContextValue {
  session: SessionState;
  /** Authenticate: store tokens, load /me, set AUTHENTICATED. */
  handleLoginSuccess: (accessToken: string, refreshToken: string) => Promise<void>;
  /** Full logout: POST /auth/logout, clear tokens/cache, set UNAUTHENTICATED. */
  handleLogout: () => Promise<void>;
  /** Re-fetch /auth/me and update workspaces in session (e.g. after workspace creation). */
  refreshSession: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

// ── Provider ─────────────────────────────────────────────────────────────────

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [session, dispatch] = useReducer(sessionReducer, {
    status: "BOOTSTRAPPING",
    user: null,
    workspaces: null,
  });

  /** Shared logic: load /me and mark session as AUTHENTICATED. */
  const loadUserAndAuthenticate = useCallback(async () => {
    const { user, workspaces } = await authApi.me();
    dispatch({ type: "SET_AUTHENTICATED", user, workspaces });
  }, []);

  /** Called by login + bootstrap to complete session establishment. */
  const handleLoginSuccess = useCallback(
    async (accessToken: string, refreshToken: string) => {
      setAccessToken(accessToken);
      setRefreshToken(refreshToken);
      await loadUserAndAuthenticate();
    },
    [loadUserAndAuthenticate]
  );

  const clearSession = useCallback(() => {
    clearAllTokens();
    queryClient.clear();
    dispatch({ type: "SET_UNAUTHENTICATED" });
  }, []);

  const handleLogout = useCallback(async () => {
    const refreshToken = getRefreshToken();
    if (refreshToken) {
      try {
        await authApi.logout({ refreshToken });
      } catch {
        // Proceed with local cleanup even if server-side logout fails
      }
    }
    clearSession();
  }, [clearSession]);

  const refreshSession = useCallback(async () => {
    try {
      await loadUserAndAuthenticate();
    } catch {
      // If /me fails here the interceptor will have already handled the 401
    }
  }, [loadUserAndAuthenticate]);

  // ── Bootstrap: attempt to restore session from sessionStorage refresh token ─

  useEffect(() => {
    // Register the callback that the Axios interceptor fires on terminal refresh failure
    registerSessionExpiredCallback(() => {
      clearSession();
    });

    const existingRefreshToken = getRefreshToken();

    if (!existingRefreshToken) {
      dispatch({ type: "SET_UNAUTHENTICATED" });
      return;
    }

    let cancelled = false;

    (async () => {
      try {
        // Rotate tokens on every bootstrap to extend session freshness
        await ensureSingleFlightRefresh();
        if (cancelled) return;

        await loadUserAndAuthenticate();
        if (cancelled) return;
      } catch {
        if (cancelled) return;
        clearSession();
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [clearSession, loadUserAndAuthenticate]);

  return (
    <AuthContext.Provider
      value={{ session, handleLoginSuccess, handleLogout, refreshSession }}
    >
      {children}
    </AuthContext.Provider>
  );
}

// ── Hook ─────────────────────────────────────────────────────────────────────

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within <AuthProvider>");
  }
  return ctx;
}

/** Convenience: returns the current user (only valid in AUTHENTICATED state). */
export function useCurrentUser(): UserResponse {
  const { session } = useAuth();
  if (session.status !== "AUTHENTICATED" || !session.user) {
    throw new Error("useCurrentUser called outside authenticated session");
  }
  return session.user;
}
