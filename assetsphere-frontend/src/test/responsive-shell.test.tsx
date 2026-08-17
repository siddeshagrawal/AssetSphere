import { fireEvent, render, screen, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { vi } from "vitest";
import { AppShell } from "@/components/layout/AppShell";

vi.mock("@/components/layout/Sidebar", () => ({
  Sidebar: ({ onNavigate }: { onNavigate?: () => void }) => (
    <nav aria-label="Mock workspace navigation">
      <button type="button" onClick={onNavigate}>Overview</button>
    </nav>
  ),
}));

vi.mock("@/features/workspaces/CreateWorkspaceDialog", () => ({
  CreateWorkspaceDialog: () => null,
}));

describe("responsive application shell", () => {
  it("opens and closes the mobile navigation without depending on the desktop sidebar", () => {
    const { container } = render(
      <MemoryRouter initialEntries={["/workspaces/workspace-1"]}>
        <Routes>
          <Route path="/workspaces/:workspaceId" element={<AppShell />}>
            <Route index element={<main>Workspace overview</main>} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    expect(container.firstElementChild).toHaveClass("h-[100dvh]");
    expect(container.firstElementChild).not.toHaveClass("h-screen");

    fireEvent.click(screen.getByRole("button", { name: "Open navigation" }));
    expect(screen.getByRole("dialog", { name: "Workspace navigation" })).toBeInTheDocument();

    fireEvent.click(within(screen.getByRole("dialog", { name: "Workspace navigation" })).getByRole("button", { name: "Overview" }));
    expect(screen.queryByRole("dialog", { name: "Workspace navigation" })).not.toBeInTheDocument();
    expect(screen.getByText("Workspace overview")).toBeInTheDocument();
  });
});
