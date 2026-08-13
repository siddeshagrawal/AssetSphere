export type WorkspaceInsightType =
  | "EXECUTIVE_BRIEF"
  | "KEY_DECISIONS"
  | "RISKS_AND_GAPS"
  | "ACTION_ITEMS"
  | "OPEN_QUESTIONS"
  | "CONTRADICTIONS"
  | "KNOWLEDGE_CHECK";

export interface GenerateWorkspaceInsightRequest {
  type: Exclude<WorkspaceInsightType, "KNOWLEDGE_CHECK">;
  focus?: string;
  modelId?: string;
}

export interface InsightCitation {
  sourceId: string;
  assetId: string;
  assetVersionId: string;
  title: string | null;
  filename: string;
  chunkOrdinal: number | null;
  snippet: string;
}

export interface WorkspaceInsightResponse {
  type: Exclude<WorkspaceInsightType, "KNOWLEDGE_CHECK">;
  summary: string;
  items: Array<{
    title: string;
    secondary: string | null;
    detail: string | null;
    severity: string | null;
    sourceIds: string[];
  }>;
  citations: InsightCitation[];
}
