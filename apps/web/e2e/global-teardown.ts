import { spawnSync } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";
import path from "node:path";
import type { FullConfig } from "@playwright/test";
import { compose, repoRootFrom, stateDirFor, wasAdopted } from "./global-setup";
import { composeArgs, composeEnv, stackMode } from "./support/stack";

/**
 * Saves the API's logs, then stops what this run started — and only that.
 *
 * Three ways out:
 *
 *   - the stack was **adopted** (already running, or `E2E_STACK=external`):
 *     nothing is stopped. Killing containers a developer started themselves, or
 *     an API they are debugging in an IDE, would be a rude surprise;
 *   - `E2E_KEEP_STACK=1`: nothing is stopped, on purpose. The next run adopts
 *     it and starts testing immediately, and the database is there to be
 *     inspected after a failure;
 *   - otherwise, `down -v`.
 *
 * The logs are written first, and that ordering is the whole point: `down`
 * destroys the containers, and with them the only account of what the server
 * did. A CI failure would otherwise leave a browser trace showing an error page
 * and nothing at all about why the API produced it.
 *
 * Whatever is left running is yours to remove:
 *
 *     docker compose -p lumo-e2e -f docker-compose.yml -f docker-compose.e2e.yml down -v
 */
export default async function globalTeardown(config: FullConfig) {
  const repoRoot = repoRootFrom(config.rootDir);
  const stateDir = stateDirFor(config, repoRoot);

  if (stackMode === "external" || wasAdopted(stateDir)) {
    console.log("[e2e] pile adoptée, pas démarrée par cette campagne — laissée en place.");
    return;
  }

  // Playwright's own output directory, asked for rather than rebuilt from
  // rootDir — which is the test directory, not the one holding the config.
  const outputDir =
    config.projects[0]?.outputDir ?? path.join(repoRoot, "apps", "web", "test-results");

  captureApiLogs(repoRoot, outputDir);

  if (process.env.E2E_KEEP_STACK === "1") {
    console.log("[e2e] E2E_KEEP_STACK=1 — la pile reste debout ; la prochaine campagne l'adoptera.");
    return;
  }

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
