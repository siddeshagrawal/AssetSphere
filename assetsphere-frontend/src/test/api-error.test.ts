import { describe, it, expect } from "vitest";
import { ApiError } from "@/types/api";
import type { ErrorResponse } from "@/types/api";

function makeErrorResponse(overrides: Partial<ErrorResponse> = {}): ErrorResponse {
  return {
    code: "TEST_ERROR",
    message: "Something went wrong",
    status: 400,
    timestamp: new Date().toISOString(),
    correlationId: "corr-123",
    violations: [],
    ...overrides,
  };
}

describe("ApiError", () => {
  it("preserves code, message, status, correlationId", () => {
    const err = new ApiError(makeErrorResponse({
      code: "VALIDATION_FAILED",
      message: "Validation error",
      status: 400,
      correlationId: "abc",
    }));

    expect(err.code).toBe("VALIDATION_FAILED");
    expect(err.message).toBe("Validation error");
    expect(err.status).toBe(400);
    expect(err.correlationId).toBe("abc");
    expect(err.name).toBe("ApiError");
    expect(err).toBeInstanceOf(Error);
    expect(err).toBeInstanceOf(ApiError);
  });

  it("isValidation() returns true when code is VALIDATION_FAILED and violations present", () => {
    const err = new ApiError(makeErrorResponse({
      code: "VALIDATION_FAILED",
      violations: [{ field: "email", message: "invalid" }],
    }));
    expect(err.isValidation()).toBe(true);
  });

  it("isValidation() returns false when no violations", () => {
    const err = new ApiError(makeErrorResponse({
      code: "VALIDATION_FAILED",
      violations: [],
    }));
    expect(err.isValidation()).toBe(false);
  });

  it("violationMap() converts violations array to field→message record", () => {
    const err = new ApiError(makeErrorResponse({
      code: "VALIDATION_FAILED",
      violations: [
        { field: "email", message: "must not be blank" },
        { field: "password", message: "too short" },
      ],
    }));
    const map = err.violationMap();
    expect(map.email).toBe("must not be blank");
    expect(map.password).toBe("too short");
  });

  it("stores retryAfterSeconds when provided", () => {
    const err = new ApiError(makeErrorResponse({ status: 429, code: "RATE_LIMITED" }), 30);
    expect(err.retryAfterSeconds).toBe(30);
  });

  it("retryAfterSeconds is null when not provided", () => {
    const err = new ApiError(makeErrorResponse());
    expect(err.retryAfterSeconds).toBeNull();
  });

  it("handles null violations gracefully", () => {
    const raw = makeErrorResponse();
    // Simulate backend omitting violations (null)
    (raw as any).violations = null;
    const err = new ApiError(raw);
    expect(err.violations).toEqual([]);
    expect(err.isValidation()).toBe(false);
  });
});
