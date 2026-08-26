import { spawnSync } from "node:child_process";
import { existsSync } from "node:fs";
import path from "node:path";
import type { FullConfig } from "@playwright/test";
import { composeArgs, composeEnv } from "./support/stack";

/**
 * The directory compose runs from — the repository root, because the API image
 * build needs `packages/contracts` in its context.
 *
 * Found by walking up until `docker-compose.yml` appears, rather than by
 * counting `..` from `config.rootDir`. That count was wrong on the first run:
 * `rootDir` is the *test* directory, `apps/web/e2e`, not the directory holding
 * the config. Searching for the file cannot be off by one, and says so plainly
 * when it fails.
 */
export function repoRootFrom(start: string): string {
  let current = path.resolve(start);

  for (;;) {
    if (existsSync(path.join(current, "docker-compose.yml"))) return current;

    const parent = path.dirname(current);
    if (parent === current) {
      throw new Error(
        `docker-compose.yml not found above ${start}. The end-to-end suite runs compose from the repository root.`,
      );
    }
    current = parent;
  }
}

/**
 * Brings up PostgreSQL and lumo-api before the suite.
 *
 * `--wait` is the whole point: both services declare a healthcheck, so compose
 * blocks until Postgres answers queries *and* the API reports UP. Without it
 * the first test races Liquibase, which is a flake that reproduces once a
 * fortnight and never on the machine you are debugging on.
 *
 * `--build` rather than a cached image: the image is built from source and the
 * contract, so a run against a stale image would test an API that no longer
 * exists in the tree. The Dockerfile is layered for exactly this, and an
 * unchanged tree rebuilds from cache in seconds.
 *
 * Set `E2E_KEEP_STACK=1` to leave the containers up afterwards — useful when a
 * test failed and the database is the evidence.
 */
export default async function globalSetup(config: FullConfig) {
  compose(repoRootFrom(config.rootDir), [
    "up",
    "-d",
    "--wait",
    "--build",
    "postgres",
    "lumo-api",
  ]);
}

export function compose(cwd: string, args: string[]) {
  const result = spawnSync("docker", [...composeArgs, ...args], {
    cwd,
    // Inherited, so a failing image build is readable in the run's output
    // instead of being swallowed and reported as a timeout.
    stdio: "inherit",
    env: { ...process.env, ...composeEnv },
    shell: false,
  });

  if (result.error) {
    throw new Error(
      `docker compose ${args.join(" ")} could not be started: ${result.error.message}. ` +
        "The end-to-end suite needs Docker (docker-compose.e2e.yml).",
    );
  }
  if (result.status !== 0) {
    throw new Error(`docker compose ${args.join(" ")} exited with ${result.status}.`);
  }
}
