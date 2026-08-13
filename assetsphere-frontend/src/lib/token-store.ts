/**
 * Token storage abstraction.
 *
 * ACCESS TOKEN — module-level variable only.
 *   Never written to localStorage, sessionStorage, or any browser storage.
 *   Lost on page reload; restored via refresh token on bootstrap.
 *   Never logged.
 *
 * REFRESH TOKEN — sessionStorage.
 *   Acknowledged compromise: the backend returns tokens in JSON (not as
 *   httpOnly cookies), so sessionStorage is the best available option.
 *   It is cleared when the browser tab closes and is NOT shared across tabs.
 *   It remains XSS-accessible — this is a known limitation until the backend
 *   switches to httpOnly cookie issuance.
 *   Never logged.
 */

const REFRESH_TOKEN_KEY = "as_rt"; // opaque key — no "token" in the name

// ── In-memory access token ───────────────────────────────────────────────────

let _accessToken: string | null = null;

export function getAccessToken(): string | null {
  return _accessToken;
}

export function setAccessToken(token: string): void {
  _accessToken = token;
}

export function clearAccessToken(): void {
  _accessToken = null;
}

// ── sessionStorage refresh token ────────────────────────────────────────────

export function getRefreshToken(): string | null {
  try {
    return sessionStorage.getItem(REFRESH_TOKEN_KEY);
  } catch {
    // sessionStorage may throw in sandboxed iframes
    return null;
  }
}

export function setRefreshToken(token: string): void {
  try {
    sessionStorage.setItem(REFRESH_TOKEN_KEY, token);
  } catch {
    // Silently fail — the user will need to re-authenticate on reload
  }
}

export function clearRefreshToken(): void {
  try {
    sessionStorage.removeItem(REFRESH_TOKEN_KEY);
  } catch {
    // ignore
  }
}

/** Clear both tokens atomically (on logout / session expiry). */
export function clearAllTokens(): void {
  clearAccessToken();
  clearRefreshToken();
}
