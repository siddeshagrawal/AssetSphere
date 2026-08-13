import type { AxiosResponse } from "axios";
import { apiClient, unwrap } from "@/lib/api-client";
import type { ApiResponse, PageResponse } from "@/types/api";
import type { WorkspaceActivity } from "@/types/activity";

export async function getWorkspaceActivity(workspaceId: string, page = 0, size = 10): Promise<PageResponse<WorkspaceActivity>> {
  const response: AxiosResponse<ApiResponse<PageResponse<WorkspaceActivity>>> = await apiClient.get(
    `/api/v1/workspaces/${workspaceId}/activity`, { params: { page, size } }
  );
  return unwrap(response);
}
