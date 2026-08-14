import { describe, expect, it } from "vitest";
import vercelConfig from "../../vercel.json";

describe("Vercel configuration", () => {
  it("rewrites SPA deep links to the Vite entry point", () => {
    expect(vercelConfig).toEqual({
      $schema: "https://openapi.vercel.sh/vercel.json",
      rewrites: [
        {
          source: "/(.*)",
          destination: "/index.html",
        },
      ],
    });
  });
});
