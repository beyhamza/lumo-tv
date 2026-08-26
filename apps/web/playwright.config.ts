import { defineConfig, devices } from "@playwright/test";
import { SESSION_FILE, WEB_PORT, webBaseUrl, webEnv } from "./e2e/support/stack";

/**
 * End-to-end tests, against the whole stack.
 *
 * `global-setup.ts` brings up PostgreSQL and lumo-api from
 * `docker-compose.e2e.yml`; this file builds and serves the web application.
 * Everything is described in `e2e/support/stack.ts` — ports, generated
 * secrets, and the compose invocation.
 *
 * A **production build**, not the dev server, because the things worth testing
 * here are properties of the production output: whether a marketing page is
 * served statically, whether the proxy redirects an anonymous request for
 * `/app`, whether `/activate` works with JavaScript disabled.
 *
 * The browsers are not installed by `pnpm install`. Once, per machine:
 *
 *     pnpm exec playwright install chromium
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  // A test that only passes locally is worse than no test: `.only` left in a
  // file fails the run on CI instead of silently skipping everything else.
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? "github" : "list",

  // Generous, because a cold run builds the API image before the first test.
  // The default budget would be spent on `docker build` and report as a
  // timeout, which says nothing about anything.
  globalTimeout: 30 * 60 * 1000,

  globalSetup: "./e2e/global-setup.ts",
  globalTeardown: "./e2e/global-teardown.ts",

  use: {
    baseURL: webBaseUrl,
    trace: "on-first-retry",
  },

  projects: [
    // Signs up once and saves the cookie jar the `journey` project reuses.
    // Matched by filename, so it is invisible to the projects below.
    {
      name: "setup",
      testMatch: /.*\.setup\.ts/,
    },
    // The two anonymous projects. They ignore journey.spec.ts, which needs a
    // session; running it here would only produce three copies of the same
    // redirect to the sign-in page.
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
      testIgnore: /journey\.spec\.ts/,
    },
    // The activation flow is used standing up with a phone in hand
    // (docs/architecture.md §4), so it is tested at that size rather than only
    // on a desktop viewport.
    {
      name: "mobile",
      use: { ...devices["Pixel 7"] },
      testIgnore: /journey\.spec\.ts/,
    },
    // Everything that crosses into lumo-api.
    {
      name: "journey",
      use: { ...devices["Desktop Chrome"], storageState: SESSION_FILE },
      testMatch: /journey\.spec\.ts/,
      dependencies: ["setup"],
    },
  ],

  webServer: {
    command: `pnpm build && pnpm start --port ${WEB_PORT}`,
    url: webBaseUrl,
    // Points the build and the server at the containers, and hands them a
    // session secret generated for this run. NEXT_PUBLIC_API_BASE_URL is
    // inlined at build time, which is why it is set on the whole command.
    env: webEnv,
    reuseExistingServer: !process.env.CI,
    timeout: 180_000,
  },
});
