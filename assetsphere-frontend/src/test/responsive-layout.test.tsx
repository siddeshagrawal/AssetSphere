import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { PageHeader } from "@/components/shared/PageHeader";
import assetDetailSource from "@/pages/assets/AssetDetailPage.tsx?raw";
import compareDialogSource from "@/features/assets/CompareVersionsDialog.tsx?raw";
import inviteDialogSource from "@/features/workspaces/InviteMemberDialog.tsx?raw";
import membersPageSource from "@/pages/members/MembersPage.tsx?raw";
import settingsPageSource from "@/pages/settings/WorkspaceSettingsPage.tsx?raw";

describe("responsive layout contracts", () => {
  it("stacks PageHeader content and wraps multiple actions before sm", () => {
    const { container } = render(
      <PageHeader title="Asset" actions={<><button>First</button><button>Second</button></>} />
    );

    expect(container.firstElementChild).toHaveClass("flex-col", "sm:flex-row");
    expect(screen.getByText("First").parentElement).toHaveClass("w-full", "flex-wrap", "sm:w-auto");
  });

  it("keeps asset detail headers and intelligence selector mobile-first", () => {
    expect(assetDetailSource).toContain("flex flex-col items-stretch gap-3 border-b");
    expect(assetDetailSource).toContain("sm:flex-row sm:items-center");
    expect(assetDetailSource).toContain('id="intelligence-model"');
    expect(assetDetailSource).toContain("h-11 w-full");
    expect(assetDetailSource).toContain("sm:w-auto sm:max-w-48");
  });

  it("uses the dynamic viewport for the evolution dialog", () => {
    expect(compareDialogSource).toContain("max-h-[calc(100dvh-2rem)]");
    expect(compareDialogSource).not.toContain("max-h-[calc(100vh-2rem)]");
  });

  it("stacks member management and workspace save controls on mobile", () => {
    expect(membersPageSource).toContain("flex flex-col items-stretch gap-3");
    expect(membersPageSource).toContain("sm:flex-row sm:items-center");
    expect(settingsPageSource).toContain("flex flex-col items-stretch gap-3 sm:flex-row");
    expect(settingsPageSource).toContain("break-all text-xs text-muted-foreground");
  });

  it("stacks invitation-created controls and safely wraps long values", () => {
    expect(inviteDialogSource).toContain("flex min-w-0 flex-col gap-2 sm:flex-row");
    expect(inviteDialogSource).toContain("min-w-0 break-all");
  });
});
