import { useMutation } from "@tanstack/react-query";
import { generateAssetVersionInsight, generateWorkspaceInsight } from "@/api/insight.api";
import type { GenerateWorkspaceInsightRequest } from "@/types/insight";

interface Variables {
  request: GenerateWorkspaceInsightRequest;
  assetId?: string;
  versionNumber?: number;
}

export function useGenerateInsight(workspaceId: string) {
  return useMutation({
    mutationFn: ({ request, assetId, versionNumber }: Variables) =>
      assetId && versionNumber !== undefined
        ? generateAssetVersionInsight(workspaceId, assetId, versionNumber, request)
        : generateWorkspaceInsight(workspaceId, request),
  });
}
