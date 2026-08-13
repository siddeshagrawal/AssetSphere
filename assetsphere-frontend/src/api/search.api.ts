import type { AxiosResponse } from "axios";
import { apiClient, unwrap } from "@/lib/api-client";
import type { ApiResponse, PageResponse } from "@/types/api";
import type { AssetSearchResult, SearchMode, WorkspaceQuestionAnswer } from "@/types/search";

export async function searchWorkspace(
  workspaceId: string,
  query: string,
  mode: SearchMode,
  page: number,
  size = 20
): Promise<PageResponse<AssetSearchResult>> {
  const response: AxiosResponse<ApiResponse<PageResponse<AssetSearchResult>>> =
    await apiClient.get(`/api/v1/workspaces/${workspaceId}/search`, {
      params: { q: query, mode, page, size },
    });
  return unwrap(response);
}

export async function askWorkspace(
  workspaceId: string,
  question: string,
  modelId?: string
): Promise<WorkspaceQuestionAnswer> {
  const response: AxiosResponse<ApiResponse<WorkspaceQuestionAnswer>> =
    await apiClient.post(`/api/v1/workspaces/${workspaceId}/ask`, { question, modelId });
  return unwrap(response);
}
