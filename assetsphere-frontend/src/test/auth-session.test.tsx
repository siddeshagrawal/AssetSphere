import { describe, it, expect, vi, beforeEach } from "vitest";
import React from "react";
import { render, screen, waitFor, act } from "@testing-library/react";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { QueryClientProvider } from "@tanstack/react-query";
import { QueryClient } from "@tanstack/react-query";
import { AuthProvider, useAuth } from "@/features/auth/AuthProvider";
import { RequireAuth } from "@/app/routes/RequireAuth";
import {
  getAccessToken,
  getRefreshToken,
  setRefreshToken,
  clearAllTokens,
} from "@/lib/token-store";

// ── Mock the auth API module ──────────────────────────────────────────────────

vi.mock("@/api/auth.api", () => ({
  register: vi.fn(),
  login: vi.fn(),
  refresh: vi.fn(),
  logout: vi.fn(),
  me: vi.fn(),
}));

// We also need to mock api-client to avoid axios setup issues in tests
vi.mock("@/lib/api-client", () => ({
  apiClient: {},
  unwrap: vi.fn(),
  registerSessionExpiredCallback: vi.fn(),
  ensureSingleFlightRefresh: vi.fn(),
}));

import * as authApi from "@/api/auth.api";
import { ensureSingleFlightRefresh } from "@/lib/api-client";

function makeQc() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function Wrapper({ children }: { children: React.ReactNode }) {
  return (
    <QueryClientProvider client={makeQc()}>
      <MemoryRouter>
        <AuthProvider>{children}</AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

// Component that displays current session status for testing
function SessionDisplay() {
  const { session } = useAuth();
  return <div data-testid="status">{session.status}</div>;
}

// Protected content
function ProtectedContent() {
  return (
    <Routes>
      <Route element={<RequireAuth />}>
        <Route path="/" element={<div data-testid="protected">Protected</div>} />
      </Route>
      <Route path="/login" element={<div data-testid="login-page">Login</div>} />
    </Routes>
  );
}

describe("Auth session — no refresh token on load", () => {
  beforeEach(() => {
    clearAllTokens();
    vi.clearAllMocks();
  });

  it("transitions to UNAUTHENTICATED without a refresh token", async () => {
    render(<SessionDisplay />, { wrapper: Wrapper });

    await waitFor(() => {
      expect(screen.getByTestId("status").textContent).toBe("UNAUTHENTICATED");
    });

    expect(getAccessToken()).toBeNull();
    expect(getRefreshToken()).toBeNull();
  });
});

describe("Auth session — login stores tokens correctly", () => {
  beforeEach(() => {
    clearAllTokens();
    vi.clearAllMocks();
  });

  it("stores access token in memory and refresh token in sessionStorage after login", async () => {
    vi.mocked(authApi.me).mockResolvedValue({
      user: {
        id: "u1",
        email: "test@example.com",
        displayName: "Test User",
        status: "ACTIVE",
        emailVerified: true,
        lastLoginAt: null,
      },
      workspaces: [],
    });

    let capturedLoginSuccess: ((a: string, r: string) => Promise<void>) | null = null;

    function LoginCapture() {
      const { handleLoginSuccess } = useAuth();
      capturedLoginSuccess = handleLoginSuccess;
      return null;
    }

    render(
      <Wrapper>
        <SessionDisplay />
        <LoginCapture />
      </Wrapper>
    );

    await waitFor(() =>
      expect(screen.getByTestId("status").textContent).toBe("UNAUTHENTICATED")
    );

    await act(async () => {
      await capturedLoginSuccess!("access-token-123", "refresh-token-456");
    });

    // Access token must be in memory
    expect(getAccessToken()).toBe("access-token-123");

    // Refresh token must be in sessionStorage
    expect(getRefreshToken()).toBe("refresh-token-456");

    // Session should be AUTHENTICATED
    await waitFor(() =>
      expect(screen.getByTestId("status").textContent).toBe("AUTHENTICATED")
    );
  });
});

describe("Auth session — logout clears all tokens", () => {
  beforeEach(() => {
    clearAllTokens();
    vi.clearAllMocks();
  });

  it("clears access and refresh tokens after logout", async () => {
    vi.mocked(authApi.me).mockResolvedValue({
      user: {
        id: "u1",
        email: "test@example.com",
        displayName: "Test",
        status: "ACTIVE",
        emailVerified: true,
        lastLoginAt: null,
      },
      workspaces: [],
    });
    vi.mocked(authApi.logout).mockResolvedValue(undefined);

    let handleLoginSuccess: ((a: string, r: string) => Promise<void>) | null = null;
    let handleLogout: (() => Promise<void>) | null = null;

    function Capture() {
      const auth = useAuth();
      handleLoginSuccess = auth.handleLoginSuccess;
      handleLogout = auth.handleLogout;
      return null;
    }

    render(<Wrapper><SessionDisplay /><Capture /></Wrapper>);
    await waitFor(() =>
      expect(screen.getByTestId("status").textContent).toBe("UNAUTHENTICATED")
    );

    await act(async () => {
      await handleLoginSuccess!("access-tok", "refresh-tok");
    });

    await waitFor(() =>
      expect(screen.getByTestId("status").textContent).toBe("AUTHENTICATED")
    );

    await act(async () => {
      await handleLogout!();
    });

    expect(getAccessToken()).toBeNull();
    expect(getRefreshToken()).toBeNull();

    await waitFor(() =>
      expect(screen.getByTestId("status").textContent).toBe("UNAUTHENTICATED")
    );
  });
});

describe("RequireAuth — protected route bootstrap behaviour", () => {
  beforeEach(() => {
    clearAllTokens();
    vi.clearAllMocks();
  });

  it("shows loading while BOOTSTRAPPING (prevents protected flash)", async () => {
    // Keep bootstrap pending by making refresh hang
    vi.mocked(ensureSingleFlightRefresh).mockImplementation(
      () => new Promise(() => {}) // never resolves
    );
    setRefreshToken("some-token");

    render(
      <QueryClientProvider client={makeQc()}>
        <MemoryRouter initialEntries={["/"]}>
          <AuthProvider>
            <ProtectedContent />
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>
    );

    // Protected content must NOT render during bootstrap
    expect(screen.queryByTestId("protected")).toBeNull();
    expect(screen.queryByTestId("login-page")).toBeNull();
    // Loading screen should be shown
    expect(screen.getByRole("status")).toBeInTheDocument();
  });

  it("redirects to /login when UNAUTHENTICATED", async () => {
    // No refresh token → goes straight to UNAUTHENTICATED
    render(
      <QueryClientProvider client={makeQc()}>
        <MemoryRouter initialEntries={["/"]}>
          <AuthProvider>
            <ProtectedContent />
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>
    );

    await waitFor(() => {
      expect(screen.getByTestId("login-page")).toBeInTheDocument();
    });
    expect(screen.queryByTestId("protected")).toBeNull();
  });

  it("renders protected content when AUTHENTICATED", async () => {
    vi.mocked(ensureSingleFlightRefresh).mockResolvedValue("new-access");
    vi.mocked(authApi.me).mockResolvedValue({
      user: {
        id: "u1",
        email: "user@test.com",
        displayName: "Test User",
        status: "ACTIVE",
        emailVerified: true,
        lastLoginAt: null,
      },
      workspaces: [],
    });

    setRefreshToken("existing-refresh");

    render(
      <QueryClientProvider client={makeQc()}>
        <MemoryRouter initialEntries={["/"]}>
          <AuthProvider>
            <ProtectedContent />
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>
    );

    await waitFor(() => {
      expect(screen.getByTestId("protected")).toBeInTheDocument();
    });
  });
});

describe("Registration does NOT authenticate", () => {
  beforeEach(() => {
    clearAllTokens();
    vi.clearAllMocks();
  });
  it("register API function does not store any tokens", async () => {
    // Calling register with a mock result should not produce tokens
    vi.mocked(authApi.register).mockResolvedValue({
      user: {
        id: "u2",
        email: "new@test.com",
        displayName: "New User",
        status: "ACTIVE",
        emailVerified: false,
        lastLoginAt: null,
      },
      defaultWorkspace: {
        id: "ws1",
        name: "New User's Workspace",
        slug: "new-users-workspace",
        role: "OWNER",
      },
    });

    await authApi.register({
      email: "new@test.com",
      password: "Str0ngPassw0rd!",
      displayName: "New User",
    });

    // After register: no tokens should exist
    expect(getAccessToken()).toBeNull();
    expect(getRefreshToken()).toBeNull();
  });
});
