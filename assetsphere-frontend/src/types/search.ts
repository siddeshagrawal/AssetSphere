export type SearchMode = "LEXICAL" | "SEMANTIC" | "HYBRID";

export interface AssetSearchResult {
  assetId: string;
  assetVersionId: string;
  versionNumber: number;
  displayName: string | null;
  originalFilename: string | null;
  mimeType: string | null;
  processingStatus: string | null;
  rank: number;
  snippet: string | null;
}

export interface WorkspaceAnswerCitation {
  sourceId: string;
  assetId: string;
  assetVersionId: string;
  title: string | null;
  filename: string | null;
  chunkOrdinal: number | null;
  snippet: string;
}

export interface WorkspaceQuestionAnswer {
  answer: string;
  citations: WorkspaceAnswerCitation[];
}
