import { useMutation } from "@tanstack/react-query";
import { askWorkspace } from "@/api/search.api";

export function useAskWorkspace(workspaceId: string) {
  return useMutation({
    mutationFn: ({ question, modelId }: { question: string; modelId?: string }) => askWorkspace(workspaceId, question, modelId),
  });
}
