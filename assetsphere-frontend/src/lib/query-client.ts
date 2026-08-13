import { QueryClient } from "@tanstack/react-query";

/**
 * Global TanStack Query client.
 *
 * Retry policy:
 *   - 401, 403, 404, 409, 429: no retries — these are definitive backend states.
 *   - 5xx / network errors: 1 retry with a short delay.
 *   - Never retry the refresh endpoint itself (handled in the Axios interceptor).
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,          // 30 s before data is considered stale
      gcTime: 5 * 60_000,         // 5 min cache retention after unmount
      refetchOnWindowFocus: false,
      retry: (failureCount, error) => {
        // Import is circular-safe because ApiError lives in types, not api-client
        const status = (error as { status?: number }).status;
        if (status !== undefined && [401, 403, 404, 409, 429].includes(status)) {
          return false;
        }
        return failureCount < 1;
      },
    },
    mutations: {
      retry: false,
    },
  },
});
