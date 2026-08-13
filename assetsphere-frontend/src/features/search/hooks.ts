import { useQuery } from "@tanstack/react-query";
import { searchWorkspace } from "@/api/search.api";
import type { SearchMode } from "@/types/search";

export function useWorkspaceSearch(
  workspaceId: string | undefined,
  query: string,
  mode: SearchMode,
  page: number
) {
  return useQuery({
    queryKey: ["search", workspaceId, query, mode, page],
    queryFn: () => searchWorkspace(workspaceId!, query, mode, page),
    enabled: !!workspaceId && query.length > 0,
    retry: (count, error) => {
      const status = (error as { status?: number }).status;
      return status !== 429 && status !== 503 && count < 1;
    },
  });
}
