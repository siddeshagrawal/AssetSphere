import { describe, expect, it } from "vitest";
import fileDropzoneSource from "@/features/assets/FileDropzone.tsx?raw";

describe("asset video upload accept configuration", () => {
  it("accepts MP4 and WebM media", () => {
    expect(fileDropzoneSource).toContain('"video/mp4"');
    expect(fileDropzoneSource).toContain('"video/webm"');
    expect(fileDropzoneSource).toContain("MP4, or WebM");
  });
});
