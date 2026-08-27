/**
 * Google sign-in, the parts both sides need (US-03).
 *
 * No `server-only` here: the client component that draws the button reads the
 * client ID, and the route handler reads the code list. Neither is a secret — a
 * `NEXT_PUBLIC_*` value is inlined into the bundle by definition, and an OAuth
 * client ID identifies the application rather than authenticating it. The client
 * *secret* has no business in this application at all: the backend verifies the
 * id_token itself.
 */

/**
 * The OAuth client ID, or nothing.
 *
 * `undefined` is a supported state and the one in a fresh checkout: no button is
 * drawn, and the email form — which is complete on its own — is all there is. A
 * button certain to fail teaches a visitor that the site is broken.
 */
export function googleClientId(): string | undefined {
  return process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID || undefined;
}

/** Where Google posts the credential. Registered as an authorised redirect URI. */
export const GOOGLE_LOGIN_PATH = "/api/auth/google";

/**
 * The refusals the sign-in page has a sentence for.
 *
 * A closed list, checked before anything from the request reaches a query
 * parameter: this endpoint is reached by a cross-site POST, and a status taken
 * from it would be a stranger choosing what the page says.
 *
 * `GENERIC` and `NETWORK` are not contract codes — they are this flow's own two
 * ways of failing before the API answers at all — and the sign-in page already
 * translates both under `Errors`.
 */
export const GOOGLE_ERROR_CODES = [
  "OAUTH_TOKEN_INVALID",
  "DEVICE_LIMIT_REACHED",
  "RATE_LIMITED",
  "VALIDATION_FAILED",
  "NETWORK",
  "GENERIC",
] as const;

export type GoogleErrorCode = (typeof GOOGLE_ERROR_CODES)[number];

/** Narrows an untrusted query parameter to something translatable, or nothing. */
export function googleErrorCode(value: unknown): GoogleErrorCode | null {
  return typeof value === "string" &&
    (GOOGLE_ERROR_CODES as readonly string[]).includes(value)
    ? (value as GoogleErrorCode)
    : null;
}
