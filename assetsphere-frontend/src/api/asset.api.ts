import type { AxiosResponse } from "axios";
import { apiClient, unwrap } from "@/lib/api-client";
import { createClientRequestId } from "@/lib/utils";
import type { ApiResponse, PageResponse } from "@/types/api";
import type {
  AssetIntelligenceResponse,
  AssetResponse,
  AssetVersionResponse,
  DownloadedAssetVersion,
  UploadAssetRequest,
  UploadAssetVersionRequest,
  UpdateAssetMetadataRequest,
  AssetEvolutionResponse,
} from "@/types/asset";

function base(workspaceId: string) {
  return `/api/v1/workspaces/${workspaceId}/assets`;
}

export async function listAssets(
  workspaceId: string,
  page = 0,
  size = 50
): Promise<PageResponse<AssetResponse>> {
  const response: AxiosResponse<ApiResponse<PageResponse<AssetResponse>>> =
    await apiClient.get(base(workspaceId), { params: { page, size } });
  return unwrap(response);
}

export async function getAsset(
  workspaceId: string,
  assetId: string
): Promise<AssetResponse> {
  const response: AxiosResponse<ApiResponse<AssetResponse>> =
    await apiClient.get(`${base(workspaceId)}/${assetId}`);
  return unwrap(response);
}

export async function updateAssetMetadata(
  workspaceId: string,
  assetId: string,
  request: UpdateAssetMetadataRequest
): Promise<AssetResponse> {
  const response: AxiosResponse<ApiResponse<AssetResponse>> = await apiClient.patch(
    `${base(workspaceId)}/${assetId}`,
    request
  );
  return unwrap(response);
}

export async function compareAssetVersions(
  workspaceId: string,
  assetId: string,
  fromVersion: number,
  toVersion: number,
  modelId?: string
): Promise<AssetEvolutionResponse> {
  const response: AxiosResponse<ApiResponse<AssetEvolutionResponse>> = await apiClient.post(
    `${base(workspaceId)}/${assetId}/compare`,
    { fromVersion, toVersion, modelId }
  );
  return unwrap(response);
}

export async function uploadAsset(
  workspaceId: string,
  request: UploadAssetRequest
): Promise<AssetResponse> {
  const form = new FormData();
  form.append("file", request.file);
  if (request.displayName?.trim()) form.append("displayName", request.displayName.trim());
  if (request.description?.trim()) form.append("description", request.description.trim());

  const response: AxiosResponse<ApiResponse<AssetResponse>> = await apiClient.post(
    base(workspaceId),
    form,
    {
      headers: {
        "Content-Type": "multipart/form-data",
        "Idempotency-Key": createClientRequestId(),
      },
      onUploadProgress: (event) => {
        if (event.total) request.onProgress?.(Math.round((event.loaded / event.total) * 100));
      },
    }
  );
  return unwrap(response);
}

export async function getAssetIntelligence(
  workspaceId: string,
  assetId: string
): Promise<AssetIntelligenceResponse> {
  const response: AxiosResponse<ApiResponse<AssetIntelligenceResponse>> =
    await apiClient.get(`${base(workspaceId)}/${assetId}/intelligence`);
  return unwrap(response);
}

export async function getAssetVersionIntelligence(
  workspaceId: string,
  assetId: string,
  versionNumber: number
): Promise<AssetIntelligenceResponse> {
  const response: AxiosResponse<ApiResponse<AssetIntelligenceResponse>> = await apiClient.get(
    `${base(workspaceId)}/${assetId}/versions/${versionNumber}/intelligence`
  );
  return unwrap(response);
}

export async function generateAssetVersionIntelligence(
  workspaceId: string,
  assetId: string,
  versionNumber: number,
  modelId?: string
): Promise<AssetIntelligenceResponse> {
  const response: AxiosResponse<ApiResponse<AssetIntelligenceResponse>> = await apiClient.post(
    `${base(workspaceId)}/${assetId}/versions/${versionNumber}/intelligence/generate`,
    modelId ? { modelId } : {}
  );
  return unwrap(response);
}

export async function uploadAssetVersion(
  workspaceId: string,
  assetId: string,
  request: UploadAssetVersionRequest
): Promise<AssetResponse> {
  const form = new FormData();
  form.append("file", request.file);
  const response: AxiosResponse<ApiResponse<AssetResponse>> = await apiClient.post(
    `${base(workspaceId)}/${assetId}/versions`,
    form,
    {
      headers: {
        "Content-Type": "multipart/form-data",
        "Idempotency-Key": request.idempotencyKey,
      },
      onUploadProgress: (event) => {
        if (event.total) request.onProgress?.(Math.round((event.loaded / event.total) * 100));
      },
    }
  );
  return unwrap(response);
}

export async function listAssetVersions(
  workspaceId: string,
  assetId: string
): Promise<AssetVersionResponse[]> {
  const response: AxiosResponse<ApiResponse<AssetVersionResponse[]>> = await apiClient.get(
    `${base(workspaceId)}/${assetId}/versions`
  );
  return unwrap(response);
}

export async function getAssetVersion(
  workspaceId: string,
  assetId: string,
  versionNumber: number
): Promise<AssetVersionResponse> {
  const response: AxiosResponse<ApiResponse<AssetVersionResponse>> = await apiClient.get(
    `${base(workspaceId)}/${assetId}/versions/${versionNumber}`
  );
  return unwrap(response);
}

export async function downloadAssetVersion(
  workspaceId: string,
  assetId: string,
  versionNumber: number
): Promise<DownloadedAssetVersion> {
  const response: AxiosResponse<Blob> = await apiClient.get(
    `${base(workspaceId)}/${assetId}/versions/${versionNumber}/download`,
    { responseType: "blob" }
  );
  return {
    blob: response.data,
    filename: filenameFromDisposition(response.headers["content-disposition"]),
  };
}

function filenameFromDisposition(header: unknown): string | null {
  if (typeof header !== "string") return null;
  const encoded = header.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  if (encoded) {
    try {
      return decodeURIComponent(encoded);
    } catch {
      return encoded;
    }
  }
  return header.match(/filename="([^"]+)"/i)?.[1] ?? header.match(/filename=([^;]+)/i)?.[1]?.trim() ?? null;
}
