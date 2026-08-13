export type AssetType = "PDF" | "DOCX" | "IMAGE" | "OTHER";
export type AssetLifecycleStatus = "ACTIVE" | "ARCHIVED" | "DELETED";
export type AssetProcessingStatus =
  | "UPLOADED"
  | "QUEUED"
  | "PROCESSING"
  | "READY"
  | "PARTIALLY_PROCESSED"
  | "FAILED";

export interface AssetResponse {
  assetId: string;
  assetVersionId: string;
  workspaceId: string;
  originalFilename: string;
  displayName: string;
  description: string | null;
  assetType: AssetType;
  mimeType: string;
  fileSize: number;
  checksum: string;
  versionNumber: number;
  lifecycleStatus: AssetLifecycleStatus;
  processingStatus: AssetProcessingStatus;
  createdAt: string | number;
}

export type IntelligenceStatus =
  | "NOT_GENERATED"
  | "PENDING"
  | "PROCESSING"
  | "READY"
  | "FAILED"
  | "NOT_APPLICABLE"
  | "DISABLED";

export interface AssetIntelligenceResponse {
  assetId: string;
  assetVersionId: string;
  status: IntelligenceStatus;
  summary: string | null;
  keyPoints: string[];
  tags: string[];
  provider: string | null;
  model: string | null;
  inputTruncated: boolean;
  generatedAt: string | number | null;
}

export interface UploadAssetRequest {
  file: File;
  displayName?: string;
  description?: string;
  onProgress?: (percent: number) => void;
}

export interface AssetVersionResponse {
  assetVersionId: string;
  assetId: string;
  versionNumber: number;
  originalFilename: string;
  displayName: string;
  mimeType: string;
  fileSize: number;
  processingStatus: AssetProcessingStatus;
  createdAt: string | number;
}

export interface UploadAssetVersionRequest {
  file: File;
  idempotencyKey: string;
  onProgress?: (percent: number) => void;
}

export interface DownloadedAssetVersion {
  blob: Blob;
  filename: string | null;
}

export interface UpdateAssetMetadataRequest {
  displayName: string;
  description: string | null;
}

export interface AssetEvolutionResponse {
  fromVersion: number;
  toVersion: number;
  executiveSummary: string;
  keyChanges: string[];
  additions: string[];
  removals: string[];
  importantChanges: string[];
}
