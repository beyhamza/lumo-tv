import { spawnSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import type { FullConfig } from "@playwright/test";
import { fingerprintApiImage } from "./support/image";
import {
  API_IMAGE,
  apiHealthUrl,
  composeArgs,
  composeEnv,
  stackMode,
} from "./support/stack";

/**
 * Makes lumo-api and PostgreSQL available to the suite — by starting them, or
 * by finding them already running.
 *
 * Three ways in, chosen with `E2E_STACK` (see `support/stack.ts`). The default,
 * `auto`, probes the API first and only starts containers when nothing answers.
 * That is what makes the pair `E2E_KEEP_STACK=1` then plain `pnpm test:e2e`
 * useful: the first run pays for the stack, every run afterwards starts testing
 * immediately.
 *
 * When containers are needed, `--wait` blocks until Postgres answers queries
 * *and* the API reports UP. Without it the first test races Liquibase, which is
 * a flake that reproduces once a fortnight and never while you are watching.
 */
export default async function globalSetup(config: FullConfig) {
  const repoRoot = repoRootFrom(config.rootDir);
  const stateDir = stateDirFor(config, repoRoot);

  if (stackMode === "external") {
    // Nothing is started and nothing will be stopped. The developer owns the
    // server — from an IDE, with a debugger attached, which is the whole point.
    if (!(await apiAnswers())) {
      throw new Error(
        `E2E_STACK=external, but nothing answers at ${apiHealthUrl}. ` +
          "Start lumo-api first, or set E2E_API_PORT to the port it listens on.",
      );
    }
    console.log(`[e2e] API externe adoptée sur ${apiHealthUrl} — Docker n'est pas touché.`);
    markAdopted(stateDir, true);
    return;
  }

  if (stackMode === "auto" && (await apiAnswers())) {
    console.log(`[e2e] pile déjà en écoute sur ${apiHealthUrl} — réutilisée telle quelle.`);
    markAdopted(stateDir, true);
    return;
  }

  markAdopted(stateDir, false);
  compose(repoRoot, ["up", "-d", "--wait", ...buildArgs(repoRoot, stateDir)]);
}

/**
 * `--build`, or not.
 *
 * Passed when the image is missing, when the API sources moved since the last
 * build, or when `E2E_API_BUILD=1` forces it. Skipping it otherwise is the
 * difference between a run that starts testing in seconds and one that spends
 * half a minute confirming nothing changed.
 */
function buildArgs(repoRoot: string, stateDir: string): string[] {
  // `bench` is pulled, never built, and `--build` on a list containing it is
  // harmless — compose builds what has a build section and starts the rest.
  const services = ["postgres", "lumo-api", "bench"];

  if (process.env.E2E_API_BUILD === "1") {
    console.log("[e2e] E2E_API_BUILD=1 — image reconstruite sur demande.");
    return ["--build", ...services];
  }

  const fingerprint = fingerprintApiImage(repoRoot);
  const stamp = path.join(stateDir, "api-image.sha256");
  const previous = existsSync(stamp) ? readFileSync(stamp, "utf8").trim() : null;

  if (previous === fingerprint && apiImageExists()) {
    console.log("[e2e] sources de l'API inchangées — image réutilisée.");
    return services;
  }

  console.log(
    previous === null
      ? "[e2e] pas d'empreinte connue — construction de l'image."
      : "[e2e] sources de l'API modifiées — reconstruction de l'image.",
  );

  // Written before the build, not after: a build that fails halfway can leave a
  // half-updated image, and the next run must not believe it is current. A
  // stale stamp costs one needless rebuild; a wrongly-fresh one costs a suite
  // that passes against code nobody wrote.
  mkdirSync(stateDir, { recursive: true });
  writeFileSync(stamp, `${fingerprint}\n`, "utf8");

  return ["--build", ...services];
}

function apiImageExists(): boolean {
  const result = spawnSync("docker", ["image", "inspect", API_IMAGE], {
    encoding: "utf8",
    stdio: ["ignore", "ignore", "ignore"],
  });
  return result.status === 0;
}

/** Whether something is already serving the API's health endpoint. */
async function apiAnswers(): Promise<boolean> {
  try {
    const response = await fetch(apiHealthUrl, {
      signal: AbortSignal.timeout(2_000),
    });
    if (!response.ok) return false;
    return (await response.text()).includes('"status":"UP"');
  } catch {
    return false;
  }
}

/**
 * Records whether this run started the stack.
 *
 * Read by the teardown, which must not stop containers it did not start. A file
 * rather than a variable, so it survives however Playwright chooses to arrange
 * its processes.
 */
function markAdopted(stateDir: string, adopted: boolean) {
  mkdirSync(stateDir, { recursive: true });
  writeFileSync(path.join(stateDir, "adopted"), adopted ? "1" : "0", "utf8");
}

export function wasAdopted(stateDir: string): boolean {
  const marker = path.join(stateDir, "adopted");
  return existsSync(marker) && readFileSync(marker, "utf8").trim() === "1";
}

/**
 * Where this suite keeps what it must remember between runs. Gitignored, and
 * next to the config rather than inside Playwright's output directory, which is
 * emptied at the start of every run.
 */
export function stateDirFor(config: FullConfig, repoRoot: string): string {
  const webDir = config.configFile
    ? path.dirname(config.configFile)
    : path.join(repoRoot, "apps", "web");
  return path.join(webDir, ".e2e");
}

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
        "The end-to-end suite needs Docker, or E2E_STACK=external and an API of your own.",
    );
  }
  if (result.status !== 0) {
    throw new Error(`docker compose ${args.join(" ")} exited with ${result.status}.`);
  }
}
