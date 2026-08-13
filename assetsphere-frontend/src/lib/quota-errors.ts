import { ApiError } from "@/types/api";

export function quotaErrorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError && error.code === "QUOTA_EXCEEDED" ? error.message : fallback;
}
