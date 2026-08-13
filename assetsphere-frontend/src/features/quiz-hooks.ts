import { useMutation, useQuery } from "@tanstack/react-query";
import { generateAssetVersionQuiz, generateWorkspaceQuiz, getAiModels } from "@/api/quiz.api";
import type { GenerateQuizRequest } from "@/types/quiz";

interface QuizGenerationVariables {
  request: GenerateQuizRequest;
  assetId?: string;
  versionNumber?: number;
}

export function useAiModels(workspaceId: string) {
  return useQuery({ queryKey: ["ai-models", workspaceId], queryFn: () => getAiModels(workspaceId), enabled: Boolean(workspaceId), staleTime: 300_000 });
}

export function useGenerateQuiz(workspaceId: string) {
  return useMutation({
    mutationFn: ({ request, assetId, versionNumber }: QuizGenerationVariables) =>
      assetId && versionNumber !== undefined
        ? generateAssetVersionQuiz(workspaceId, assetId, versionNumber, request)
        : generateWorkspaceQuiz(workspaceId, request),
  });
}
