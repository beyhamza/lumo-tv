/**
 * Values that are allowed to reach the browser.
 *
 * Every `NEXT_PUBLIC_*` variable is inlined into the JavaScript bundle at build
 * time and readable by anyone who opens the page. This file exists so that fact
 * is visible in one place: if a value is here, it is published.
 *
 * The references are written out in full rather than looked up dynamically —
 * `process.env[name]` is not inlined by the bundler and resolves to undefined in
 * the browser.
 */

export function siteUrl(): string {
  return process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000";
}

/** Base URL of lumo-api as reached from the browser. */
export function publicApiBaseUrl(): string {
  return process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/v1";
}

/**
 * The version of this build, for "Help and information" in Settings.
 *
 * Stamped by `next.config.ts` from `package.json` at build time, unless the
 * deployment sets `NEXT_PUBLIC_APP_VERSION` itself — a release pipeline that
 * tags builds has a better name for one than the package's field. Nothing in
 * the browser reads it today; it is public because a version is not a secret,
 * and because `env` in `next.config.ts` publishes whatever it is given anyway.
 *
 * `undefined` when neither is set, and the page shows nothing rather than a
 * placeholder: the design asks for the version actually deployed, never a
 * constant (docs/design/0.2.0/settings.md).
 */
export function appVersion(): string | undefined {
  return process.env.NEXT_PUBLIC_APP_VERSION || undefined;
}
