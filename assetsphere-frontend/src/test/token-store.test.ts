import { describe, it, expect, beforeEach } from "vitest";
import {
  getAccessToken,
  setAccessToken,
  clearAccessToken,
  getRefreshToken,
  setRefreshToken,
  clearRefreshToken,
  clearAllTokens,
} from "@/lib/token-store";

describe("token-store — access token (in-memory)", () => {
  beforeEach(() => {
    clearAccessToken();
  });

  it("starts null", () => {
    expect(getAccessToken()).toBeNull();
  });

  it("stores and retrieves access token", () => {
    setAccessToken("access-abc");
    expect(getAccessToken()).toBe("access-abc");
  });

  it("clears access token", () => {
    setAccessToken("access-abc");
    clearAccessToken();
    expect(getAccessToken()).toBeNull();
  });

  it("access token is NEVER written to localStorage", () => {
    setAccessToken("secret-access");
    // Scan all localStorage keys for any value containing the token
    const found = Object.values(localStorage).some((v) =>
      typeof v === "string" && v.includes("secret-access")
    );
    expect(found).toBe(false);
  });

  it("access token is NEVER written to sessionStorage", () => {
    setAccessToken("secret-access");
    const found = Object.values(sessionStorage).some((v) =>
      typeof v === "string" && v.includes("secret-access")
    );
    expect(found).toBe(false);
  });
});

describe("token-store — refresh token (sessionStorage)", () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  it("starts null when sessionStorage is empty", () => {
    expect(getRefreshToken()).toBeNull();
  });

  it("stores refresh token in sessionStorage", () => {
    setRefreshToken("refresh-xyz");
    expect(getRefreshToken()).toBe("refresh-xyz");
    // Confirm it actually went into sessionStorage
    expect(sessionStorage.getItem("as_rt")).toBe("refresh-xyz");
  });

  it("clears refresh token from sessionStorage", () => {
    setRefreshToken("refresh-xyz");
    clearRefreshToken();
    expect(getRefreshToken()).toBeNull();
    expect(sessionStorage.getItem("as_rt")).toBeNull();
  });

  it("refresh token is NEVER written to localStorage", () => {
    setRefreshToken("refresh-xyz");
    const found = Object.values(localStorage).some((v) =>
      typeof v === "string" && v.includes("refresh-xyz")
    );
    expect(found).toBe(false);
  });
});

describe("clearAllTokens", () => {
  it("clears both access and refresh tokens", () => {
    setAccessToken("access-1");
    setRefreshToken("refresh-1");

    clearAllTokens();

    expect(getAccessToken()).toBeNull();
    expect(getRefreshToken()).toBeNull();
  });
});

describe("refresh token rotation", () => {
  it("replaces the old refresh token with the new one", () => {
    setRefreshToken("old-refresh");
    expect(getRefreshToken()).toBe("old-refresh");

    setRefreshToken("new-refresh");
    expect(getRefreshToken()).toBe("new-refresh");
    // Old value gone
    expect(sessionStorage.getItem("as_rt")).toBe("new-refresh");
  });
});
