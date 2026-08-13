import type { AxiosResponse } from "axios";
import { apiClient, unwrap } from "@/lib/api-client";
import type { ApiResponse } from "@/types/api";
import type { GenerateWorkspaceInsightRequest, WorkspaceInsightResponse } from "@/types/insight";

export async function generateWorkspaceInsight(
  workspaceId: string,
  request: GenerateWorkspaceInsightRequest
): Promise<WorkspaceInsightResponse> {
  const response: AxiosResponse<ApiResponse<WorkspaceInsightResponse>> =
    await apiClient.post(`/api/v1/workspaces/${workspaceId}/insights`, request);
  return unwrap(response);
}

export async function generateAssetVersionInsight(
  workspaceId: string,
  assetId: string,
  versionNumber: number,
  request: GenerateWorkspaceInsightRequest
): Promise<WorkspaceInsightResponse> {
  const response: AxiosResponse<ApiResponse<WorkspaceInsightResponse>> = await apiClient.post(
    `/api/v1/workspaces/${workspaceId}/assets/${assetId}/versions/${versionNumber}/insights`, request
  );
  return unwrap(response);
}
