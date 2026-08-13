import type { AxiosResponse } from "axios";
import { apiClient, unwrap } from "@/lib/api-client";
import type { ApiResponse } from "@/types/api";
import type { AiModelDescriptor, GenerateQuizRequest, QuizResponse } from "@/types/quiz";

export async function getAiModels(workspaceId: string): Promise<AiModelDescriptor[]> {
  const response: AxiosResponse<ApiResponse<AiModelDescriptor[]>> = await apiClient.get(`/api/v1/workspaces/${workspaceId}/ai/models`);
  return unwrap(response);
}

export async function generateWorkspaceQuiz(workspaceId: string, request: GenerateQuizRequest): Promise<QuizResponse> {
  const response: AxiosResponse<ApiResponse<QuizResponse>> = await apiClient.post(
    `/api/v1/workspaces/${workspaceId}/quiz`,
    request
  );
  return unwrap(response);
}

export async function generateAssetVersionQuiz(
  workspaceId: string,
  assetId: string,
  versionNumber: number,
  request: GenerateQuizRequest
): Promise<QuizResponse> {
  const response: AxiosResponse<ApiResponse<QuizResponse>> = await apiClient.post(
    `/api/v1/workspaces/${workspaceId}/assets/${assetId}/versions/${versionNumber}/quiz`,
    request
  );
  return unwrap(response);
}
