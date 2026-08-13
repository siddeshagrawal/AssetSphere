import { useQuery } from "@tanstack/react-query";
import { getWorkspaceActivity } from "@/api/activity.api";

export function useWorkspaceActivity(workspaceId: string | undefined, size = 10) {
  return useQuery({
    queryKey: ["activity", workspaceId, size],
    queryFn: () => getWorkspaceActivity(workspaceId!, 0, size),
    enabled: Boolean(workspaceId),
  });
}
