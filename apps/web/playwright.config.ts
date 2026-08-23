import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end tests.
 *
 * They run against a **production build**, not the dev server, because the
 * things worth testing here are properties of the production output: whether a
 * marketing page is served statically, whether the proxy redirects an anonymous
 * request for `/app`, whether `/activate` works with JavaScript disabled.
 *
 * The browsers are not installed by `pnpm install`. Once, per machine:
 *
 *     pnpm exec playwright install chromium
 */
const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:3000";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  // A test that only passes locally is worse than no test: `.only` left in a
  // file fails the run on CI instead of silently skipping everything else.
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? "github" : "list",

  use: {
    baseURL,
    trace: "on-first-retry",
  },

  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
    // The activation flow is used standing up with a phone in hand
    // (docs/architecture.md §4), so it is tested at that size rather than only
    // on a desktop viewport.
    { name: "mobile", use: { ...devices["Pixel 7"] } },
  ],

  webServer: {
    command: "pnpm build && pnpm start --port 3000",
    url: baseURL,
    reuseExistingServer: !process.env.CI,
    timeout: 180_000,
  },
});
