/**
 * Mirror of backend com.assetsphere.modules.common.web.ApiResponse<T>
 *
 * Every successful response is wrapped in this shape.
 */
export interface ApiResponse<T> {
  success: boolean;
  data: T;
  timestamp: string;
  correlationId: string | null;
}

/**
 * Mirror of backend com.assetsphere.modules.common.web.ErrorResponse
 *
 * Every error response (4xx / 5xx) arrives in this shape.
 */
export interface ErrorResponse {
  code: string;
  message: string;
  status: number;
  timestamp: string;
  correlationId: string | null;
  violations: FieldViolation[];
}

export interface FieldViolation {
  field: string;
  message: string;
}

/**
 * Mirror of backend com.assetsphere.modules.common.web.PageResponse<T>
 */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/**
 * Typed frontend error derived from a parsed ErrorResponse.
 *
 * Thrown by the API client instead of raw Axios errors so that
 * callers never need to catch (error: unknown) and cast.
 */
export class ApiError extends Error {
  readonly code: string;
  readonly status: number;
  readonly violations: FieldViolation[];
  readonly correlationId: string | null;
  /**
   * Populated when the backend returns a Retry-After header (HTTP 429).
   * Value is in seconds.
   */
  readonly retryAfterSeconds: number | null;

  constructor(
    errorResponse: ErrorResponse,
    retryAfterSeconds: number | null = null
  ) {
    super(errorResponse.message);
    this.name = "ApiError";
    this.code = errorResponse.code;
    this.status = errorResponse.status;
    this.violations = errorResponse.violations ?? [];
    this.correlationId = errorResponse.correlationId;
    this.retryAfterSeconds = retryAfterSeconds;
  }

  /** True when the error is a validation failure with field-level details. */
  isValidation(): boolean {
    return this.code === "VALIDATION_FAILED" && this.violations.length > 0;
  }

  /** Returns a map of field → first violation message. */
  violationMap(): Record<string, string> {
    return Object.fromEntries(
      this.violations.map((v) => [v.field, v.message])
    );
  }
}
