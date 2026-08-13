import type { AssetType } from "@/types/asset";

const LABELS_BY_EXTENSION: Record<string, string> = {
  pdf: "PDF",
  docx: "Word document",
  txt: "Text document",
  md: "Markdown",
  csv: "CSV",
  json: "JSON",
  xlsx: "Excel workbook",
  pptx: "PowerPoint presentation",
  png: "PNG image",
  jpg: "JPEG image",
  jpeg: "JPEG image",
  webp: "WebP image",
  mp4: "MP4 video",
  webm: "WebM video",
};

export function friendlyFileType(filename: string, mimeType: string, fallback?: AssetType): string {
  const extension = filename.toLowerCase().split(".").pop();
  if (extension && LABELS_BY_EXTENSION[extension]) return LABELS_BY_EXTENSION[extension];
  if (mimeType.startsWith("image/")) return "Image";
  if (mimeType.startsWith("video/")) return "Video";
  return fallback === "OTHER" || !fallback ? mimeType : fallback;
}
