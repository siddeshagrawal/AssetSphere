import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import * as assetApi from "@/api/asset.api";
import { useAuth } from "@/features/auth/AuthProvider";
import type { UpdateAssetMetadataRequest, UploadAssetRequest, UploadAssetVersionRequest } from "@/types/asset";
import { quotaErrorMessage } from "@/lib/quota-errors";

export const assetKeys = {
  all: (workspaceId: string) => ["assets", workspaceId] as const,
  list: (workspaceId: string, page: number) =>
    [...assetKeys.all(workspaceId), "list", page] as const,
  detail: (workspaceId: string, assetId: string) =>
    [...assetKeys.all(workspaceId), "detail", assetId] as const,
  intelligence: (workspaceId: string, assetId: string, versionNumber: number) =>
    [...assetKeys.detail(workspaceId, assetId), "intelligence", versionNumber] as const,
  versions: (workspaceId: string, assetId: string) =>
    [...assetKeys.detail(workspaceId, assetId), "versions"] as const,
};

function isProcessing(status: string) {
  return status === "UPLOADED" || status === "QUEUED" || status === "PROCESSING";
}

export function useAssets(workspaceId: string | undefined, page: number) {
  const { session } = useAuth();
  return useQuery({
    queryKey: assetKeys.list(workspaceId ?? "", page),
    queryFn: () => assetApi.listAssets(workspaceId!, page, 20),
    enabled: session.status === "AUTHENTICATED" && !!workspaceId,
    refetchInterval: (query) =>
      query.state.data?.content.some((asset) => isProcessing(asset.processingStatus))
        ? 4_000
        : false,
  });
}

export function useAsset(workspaceId: string | undefined, assetId: string | undefined) {
  const { session } = useAuth();
  return useQuery({
    queryKey: assetKeys.detail(workspaceId ?? "", assetId ?? ""),
    queryFn: () => assetApi.getAsset(workspaceId!, assetId!),
    enabled: session.status === "AUTHENTICATED" && !!workspaceId && !!assetId,
    retry: (count, error) => (error as { status?: number }).status !== 404 && count < 1,
    refetchInterval: (query) =>
      query.state.data && isProcessing(query.state.data.processingStatus) ? 4_000 : false,
  });
}

export function useAssetIntelligence(
  workspaceId: string | undefined,
  assetId: string | undefined,
  versionNumber: number | undefined,
  enabled: boolean
) {
  return useQuery({
    queryKey: assetKeys.intelligence(workspaceId ?? "", assetId ?? "", versionNumber ?? 0),
    queryFn: () => assetApi.getAssetVersionIntelligence(workspaceId!, assetId!, versionNumber!),
    enabled: enabled && !!workspaceId && !!assetId && !!versionNumber,
    retry: false,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === "PENDING" || status === "PROCESSING" ? 4_000 : false;
    },
  });
}

export function useGenerateAssetIntelligence(
  workspaceId: string,
  assetId: string,
  versionNumber: number
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (modelId?: string) => assetApi.generateAssetVersionIntelligence(workspaceId, assetId, versionNumber, modelId),
    onSuccess: (intelligence) => {
      queryClient.setQueryData(assetKeys.intelligence(workspaceId, assetId, versionNumber), intelligence);
      toast.success(intelligence.status === "READY" ? "AI insights are ready." : "AI insight generation started.");
    },
    onError: (error) => toast.error(quotaErrorMessage(error, "AI insight generation could not be started.")),
  });
}

export function useAssetVersions(workspaceId: string | undefined, assetId: string | undefined) {
  const { session } = useAuth();
  return useQuery({
    queryKey: assetKeys.versions(workspaceId ?? "", assetId ?? ""),
    queryFn: () => assetApi.listAssetVersions(workspaceId!, assetId!),
    enabled: session.status === "AUTHENTICATED" && !!workspaceId && !!assetId,
    refetchInterval: (query) =>
      query.state.data?.some((version) => isProcessing(version.processingStatus)) ? 4_000 : false,
  });
}

export function useUploadAsset(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: UploadAssetRequest) => assetApi.uploadAsset(workspaceId, request),
    onSuccess: async (asset) => {
      await queryClient.invalidateQueries({ queryKey: assetKeys.all(workspaceId) });
      queryClient.setQueryData(assetKeys.detail(workspaceId, asset.assetId), asset);
      toast.success(`${asset.originalFilename} uploaded.`);
    },
    onError: (error) => toast.error(quotaErrorMessage(error, "Upload failed. Please try again.")),
  });
}

export function useUploadAssetVersion(workspaceId: string, assetId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: UploadAssetVersionRequest) =>
      assetApi.uploadAssetVersion(workspaceId, assetId, request),
    onSuccess: async (asset) => {
      queryClient.setQueryData(assetKeys.detail(workspaceId, assetId), asset);
      await queryClient.invalidateQueries({ queryKey: assetKeys.all(workspaceId) });
      toast.success(`Version ${asset.versionNumber} uploaded. Processing has started.`);
    },
    onError: (error) => toast.error(quotaErrorMessage(error, "Version upload failed. You can safely retry.")),
  });
}

export function useUpdateAssetMetadata(workspaceId: string, assetId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: UpdateAssetMetadataRequest) => assetApi.updateAssetMetadata(workspaceId, assetId, request),
    onSuccess: async (asset) => {
      queryClient.setQueryData(assetKeys.detail(workspaceId, assetId), asset);
      await queryClient.invalidateQueries({ queryKey: assetKeys.all(workspaceId) });
      toast.success("Asset details updated.");
    },
    onError: () => toast.error("Asset details could not be updated."),
  });
}

export function useCompareAssetVersions(workspaceId: string, assetId: string) {
  return useMutation({
    mutationFn: ({ fromVersion, toVersion, modelId }: { fromVersion: number; toVersion: number; modelId?: string }) =>
      assetApi.compareAssetVersions(workspaceId, assetId, fromVersion, toVersion, modelId),
  });
}

export function useDownloadAssetVersion(workspaceId: string) {
  return useMutation({
    mutationFn: ({ assetId, versionNumber }: { assetId: string; versionNumber: number; fallbackFilename: string }) =>
      assetApi.downloadAssetVersion(workspaceId, assetId, versionNumber),
    onSuccess: (download, variables) => {
      const objectUrl = URL.createObjectURL(download.blob);
      const anchor = document.createElement("a");
      anchor.href = objectUrl;
      anchor.download = download.filename || variables.fallbackFilename;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 0);
    },
    onError: () => toast.error("Download failed. Please try again."),
  });
}
