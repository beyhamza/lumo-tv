import { randomBytes } from "node:crypto";

/**
 * The end-to-end stack, described once and read by everything that needs it.
 *
 * Three processes: PostgreSQL and lumo-api in containers (started by
 * `global-setup.ts` from `docker-compose.e2e.yml`), and the web application
 * built and served by Playwright's own `webServer`.
 *
 * This module is imported by `playwright.config.ts`, so it is evaluated **once
 * per run, before anything starts**. That is what lets the same generated
 * secrets reach both the containers and the Next.js server without a file on
 * disk, and it is why the order in which Playwright starts them does not
 * matter.
 */

const base64 = (bytes: number) => randomBytes(bytes).toString("base64");

/** Compose project name. Distinct from the development stack's, deliberately. */
export const COMPOSE_PROJECT = "lumo-e2e";

/**
 * The image compose builds, named the way compose names it: project, then
 * service.
 *
 * Addressed directly rather than through `docker compose images`, which lists
 * the images of *containers* — and reports nothing at all once `down` has
 * removed them, which is precisely the state every run starts from.
 */
export const API_IMAGE = `${COMPOSE_PROJECT}-lumo-api`;

/**
 * Where lumo-api comes from.
 *
 * | `E2E_STACK` | Effet |
 * |---|---|
 * | `auto` (défaut) | réutilise une API déjà en écoute ; sinon démarre la pile |
 * | `docker` | démarre toujours la pile, même si quelque chose écoute |
 * | `external` | ne touche jamais à Docker : l'API tourne ailleurs |
 *
 * `external` is the IDE case: run lumo-api from IntelliJ, with a debugger and
 * hot reload, and point the suite at it. `auto` covers the same case without
 * asking, and covers the one that actually saves time day to day — a stack left
 * up by `E2E_KEEP_STACK=1`, adopted by every run afterwards.
 *
 * A stack this run adopted is never torn down: stopping containers a developer
 * started themselves would be a rude surprise.
 */
export type StackMode = "auto" | "docker" | "external";

export const stackMode: StackMode = (() => {
  const raw = process.env.E2E_STACK ?? "auto";
  if (raw === "auto" || raw === "docker" || raw === "external") return raw;
  throw new Error(`E2E_STACK must be auto, docker or external — got "${raw}".`);
})();

/**
 * Ports.
 *
 * Not the development defaults (5432, 8080, 3000): an end-to-end run must be
 * startable while a developer's own stack is up, and — worse than a port
 * clash — must never reach that stack's database by accident.
 */
export const POSTGRES_PORT = process.env.E2E_POSTGRES_PORT ?? "55432";
export const WEB_PORT = process.env.E2E_WEB_PORT ?? "3100";

/**
 * 18080 for the suite's own stack; 8080 in `external` mode, because an API
 * started from an IDE listens on the port `application.yml` declares.
 *
 * `E2E_API_PORT` overrides either. Note that `auto` probes *this* port and not
 * "any API anywhere": adopting whatever happens to answer on 8080 would mean a
 * forgotten development stack silently becoming the thing under test.
 */
export const API_PORT =
  process.env.E2E_API_PORT ?? (stackMode === "external" ? "8080" : "18080");

export const apiBaseUrl = `http://localhost:${API_PORT}/v1`;
export const apiHealthUrl = `http://localhost:${API_PORT}/actuator/health`;
export const webBaseUrl =
  process.env.PLAYWRIGHT_BASE_URL ?? `http://localhost:${WEB_PORT}`;

/**
 * Secrets, generated per run and never written anywhere.
 *
 * Not read from a file, and no committed defaults: `.env` files are refused by
 * the `no-content` CI job, and a well-known test key has a way of becoming a
 * well-known production key. They live for the length of one run, in the
 * environment of three processes.
 *
 * `masterKey` must decode to exactly 32 bytes or the API refuses to boot
 * (AES-256, `EnvironmentMasterKeyProvider`). `sessionSecret` must be at least
 * 32 characters; 32 random bytes in base64 is 44.
 */
const secrets = {
  postgresPassword: base64(18),
  jwtSecret: base64(48),
  masterKey: base64(32),
  sessionSecret: base64(32),
};

/** Environment for `docker compose`. */
export const composeEnv: Record<string, string> = {
  POSTGRES_DB: "lumo_e2e",
  POSTGRES_USER: "lumo",
  POSTGRES_PASSWORD: secrets.postgresPassword,
  POSTGRES_PORT,
  LUMO_API_PORT: API_PORT,
  LUMO_JWT_SECRET: secrets.jwtSecret,
  LUMO_ENCRYPTION_MASTER_KEY: secrets.masterKey,
  LUMO_WEB_BASE_URL: webBaseUrl,
  LUMO_CORS_ALLOWED_ORIGINS: webBaseUrl,
  SPRING_PROFILES_ACTIVE: "dev",
};

/**
 * Environment for `pnpm build && pnpm start`.
 *
 * `NEXT_PUBLIC_API_BASE_URL` is inlined at **build** time, which is why it is
 * set on the whole command rather than only on the server: setting it later
 * would leave the browser bundle pointing at port 8080.
 */
export const webEnv: Record<string, string> = {
  API_BASE_URL: apiBaseUrl,
  NEXT_PUBLIC_API_BASE_URL: apiBaseUrl,
  NEXT_PUBLIC_SITE_URL: webBaseUrl,
  SESSION_SECRET: secrets.sessionSecret,
  SESSION_COOKIE_NAME: "lumo_session",
  // http://localhost, so the browser would silently drop a Secure cookie and
  // every sign-in would appear to succeed and then do nothing.
  SESSION_COOKIE_SECURE: "false",
  NEXT_TELEMETRY_DISABLED: "1",
};

/** Where the authenticated project's cookie jar is written. Gitignored. */
export const SESSION_FILE = "e2e/.auth/user.json";

/** Arguments common to every compose invocation of this stack. */
export const composeArgs = [
  "compose",
  "-p",
  COMPOSE_PROJECT,
  "-f",
  "docker-compose.yml",
  "-f",
  "docker-compose.e2e.yml",
];
