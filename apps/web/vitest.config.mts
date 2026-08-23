import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

/**
 * Unit tests only — Node environment, no jsdom, no React rendering.
 *
 * AGENTS.md §5: domain logic ships with its tests, UI does not need exhaustive
 * coverage. What is worth testing here is the logic that is invisible in review
 * and expensive when wrong: the session cookie, the open-redirect guard, the
 * activation-code normalisation. Rendering is covered end to end by Playwright.
 */
export default defineConfig({
  test: {
    environment: "node",
    include: ["src/**/*.test.ts"],
  },
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
      // `server-only` throws by design when it is imported outside a React
      // Server Component graph — which is the whole point of the package, and
      // also what would make every test of a server module fail. Vitest is a
      // server, so the guard is stubbed out here rather than removed from the
      // modules that need it in the build.
      "server-only": fileURLToPath(new URL("./test/stubs/server-only.ts", import.meta.url)),
    },
  },
});
