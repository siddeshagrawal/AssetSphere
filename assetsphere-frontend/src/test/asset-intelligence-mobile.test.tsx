import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { IntelligenceContent } from "@/pages/assets/AssetDetailPage";
import type { AssetIntelligenceResponse } from "@/types/asset";

describe("asset intelligence mobile rendering", () => {
  it("renders long generated content with natural height and safe wrapping", () => {
    const summary = "Summary with `inline code` and https://example.com/" + "very-long-segment".repeat(20);
    const keyPoint = "Key point " + "unbroken-value".repeat(20);
    const tag = "long-tag-" + "segment".repeat(20);
    const intelligence: AssetIntelligenceResponse = {
      assetId: "asset-1",
      assetVersionId: "version-1",
      status: "READY",
      summary,
      keyPoints: [keyPoint],
      tags: [tag],
      provider: "OPENAI",
      model: "model",
      inputTruncated: false,
      generatedAt: "2026-08-18T00:00:00Z",
    };

    const { container } = render(
      <IntelligenceContent intelligence={intelligence} generating={false} onGenerate={vi.fn()} />
    );

    expect(container.firstElementChild).toHaveClass("min-w-0", "overflow-visible");
    expect(container.firstElementChild).not.toHaveClass("overflow-y-auto", "min-h-screen", "h-screen");
    expect(screen.getByText(summary)).toHaveClass("whitespace-pre-wrap", "break-words", "[overflow-wrap:anywhere]");
    expect(screen.getByText(keyPoint)).toHaveClass("min-w-0", "break-words", "[overflow-wrap:anywhere]");
    expect(screen.getByText(tag)).toHaveClass("max-w-full", "break-words", "[overflow-wrap:anywhere]");
  });
});
