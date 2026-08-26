import { spawnSync } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";
import path from "node:path";
import type { FullConfig } from "@playwright/test";
import { compose, repoRootFrom } from "./global-setup";
import { composeArgs, composeEnv } from "./support/stack";

/**
 * Saves the API's logs, then tears the stack down.
 *
 * The logs come first and that ordering is the whole point. `down` destroys the
 * containers, and with them the only account of what the server did — so a
 * failure on CI would otherwise leave a browser trace showing an error page and
 * nothing at all about why the API produced it. The file lands in
 * `test-results/`, which the workflow already uploads on failure.
 *
 * Skipped entirely when `E2E_KEEP_STACK=1`: the containers stay up and the
 * database is there to be inspected. Yours to remove afterwards:
 *
 *     docker compose -p lumo-e2e -f docker-compose.yml -f docker-compose.e2e.yml down -v
 */
export default async function globalTeardown(config: FullConfig) {
  const repoRoot = repoRootFrom(config.rootDir);

  if (process.env.E2E_KEEP_STACK === "1") {
    console.log("[e2e] E2E_KEEP_STACK=1 — the stack is left running.");
    return;
  }

  // Playwright's own output directory, asked for rather than rebuilt from
  // rootDir — which is the test directory, not the one holding the config.
  const outputDir =
    config.projects[0]?.outputDir ?? path.join(repoRoot, "apps", "web", "test-results");

  captureApiLogs(repoRoot, outputDir);
  compose(repoRoot, ["down", "-v"]);
}

function captureApiLogs(repoRoot: string, outputDir: string) {
  const result = spawnSync(
    "docker",
    [...composeArgs, "logs", "--no-color", "--timestamps", "lumo-api"],
    { cwd: repoRoot, encoding: "utf8", env: { ...process.env, ...composeEnv } },
  );

  if (result.status !== 0 || !result.stdout) return;

  try {
    mkdirSync(outputDir, { recursive: true });
    writeFileSync(path.join(outputDir, "lumo-api.log"), result.stdout, "utf8");
  } catch (error) {
    // Never fail a green run over a diagnostic file.
    console.warn(`[e2e] could not write lumo-api.log: ${String(error)}`);
  }
}
