import "server-only";

/**
 * Server-side configuration, read once and validated loudly.
 *
 * `import "server-only"` is the load-bearing line: it makes the build fail if
 * this module is ever pulled into a client bundle, which is the only mechanical
 * guarantee that `SESSION_SECRET` cannot leak into the browser. A convention
 * would not survive one careless import.
 *
 * Public values are read separately in `env.public.ts`, because `NEXT_PUBLIC_*`
 * is not a naming convention — it is a publication. Anything with that prefix is
 * inlined into the JavaScript bundle and readable by anyone who opens the page.
 */

function required(name: string, value: string | undefined): string {
  if (!value || value.trim() === "") {
    throw new Error(
      `Missing environment variable ${name}. See apps/web/.env.example.`,
    );
  }
  return value;
}

/**
 * How the Next.js server reaches lumo-api.
 *
 * Distinct from the browser's base URL on purpose: in a deployment the API can
 * live on an internal network the browser cannot see (docs/architecture.md §7).
 */
export function apiBaseUrl(): string {
  return (
    process.env.API_BASE_URL ??
    process.env.NEXT_PUBLIC_API_BASE_URL ??
    "http://localhost:8080/v1"
  );
}

/**
 * The key that encrypts the session cookie.
 *
 * No default, ever. A default would mean every deployment that forgot to set it
 * shares one key, and anyone who reads this repository can then forge a session
 * cookie for any account.
 *
 * Read lazily rather than at module load: a missing secret must break signing in,
 * not break `next build`, which has no secrets and renders the marketing pages.
 */
export function sessionSecret(): string {
  const secret = required("SESSION_SECRET", process.env.SESSION_SECRET);
  if (secret.length < 32) {
    throw new Error(
      "SESSION_SECRET must be at least 32 characters. Generate one with: openssl rand -base64 32",
    );
  }
  return secret;
}

export function sessionCookieName(): string {
  return process.env.SESSION_COOKIE_NAME ?? "lumo_session";
}

/**
 * `Secure` must be false on plain http://localhost and true everywhere else.
 * Get it wrong locally and the browser silently drops the cookie: every sign-in
 * appears to succeed and then does nothing at all.
 */
export function sessionCookieSecure(): boolean {
  return process.env.SESSION_COOKIE_SECURE === "true";
}
