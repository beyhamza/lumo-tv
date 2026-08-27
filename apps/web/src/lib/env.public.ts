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

// The Google OAuth client ID lives with the rest of that flow, in
// `lib/auth/google.ts`. It is published like everything in this file, and it is
// read by the button and by nothing else.
