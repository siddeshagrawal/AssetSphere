import { fireEvent, render } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { FileDropzone } from "@/features/assets/FileDropzone";

const free = { ocrEnabled: false, videoTranscriptionEnabled: false };
const paid = { ocrEnabled: true, videoTranscriptionEnabled: true };

function select(file: File, entitlements = free) {
  const onFileChange = vi.fn();
  const onValidationError = vi.fn();
  const { container } = render(
    <FileDropzone
      file={null}
      inputId="asset-file"
      mediaEntitlements={entitlements}
      onFileChange={onFileChange}
      onValidationError={onValidationError}
    />
  );
  const input = container.querySelector("input[type='file']") as HTMLInputElement;
  fireEvent.change(input, { target: { files: [file] } });
  return { onFileChange, onValidationError };
}

describe("upload media entitlements", () => {
  it("allows normal documents for FREE workspaces", () => {
    const result = select(new File(["content"], "report.pdf", { type: "application/pdf" }));

    expect(result.onFileChange).toHaveBeenCalledOnce();
    expect(result.onValidationError).toHaveBeenLastCalledWith(null);
  });

  it("prevents unsupported FREE video before upload", () => {
    const result = select(new File(["video"], "demo.mp4", { type: "video/mp4" }));

    expect(result.onFileChange).not.toHaveBeenCalled();
    expect(result.onValidationError).toHaveBeenCalledWith(expect.stringMatching(/PRO or ENTERPRISE.*upgrade/i));
  });

  it("matches the existing OCR policy for FREE images", () => {
    const result = select(new File(["image"], "scan.png", { type: "image/png" }));

    expect(result.onFileChange).not.toHaveBeenCalled();
    expect(result.onValidationError).toHaveBeenCalledWith(expect.stringMatching(/Image OCR.*PRO or ENTERPRISE/i));
  });

  it("allows entitled PRO or ENTERPRISE media uploads", () => {
    const video = select(new File(["video"], "demo.webm", { type: "video/webm" }), paid);
    const image = select(new File(["image"], "scan.webp", { type: "image/webp" }), paid);

    expect(video.onFileChange).toHaveBeenCalledOnce();
    expect(image.onFileChange).toHaveBeenCalledOnce();
  });
});
