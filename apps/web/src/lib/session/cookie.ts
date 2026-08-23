import "server-only";

import { EncryptJWT, jwtDecrypt } from "jose";
import {
  sessionCookieName,
  sessionCookieSecure,
  sessionSecret,
} from "@/lib/env";

/**
 * The session, and why it lives in an encrypted cookie.
 *
 * docs/architecture.md §5 puts the web session in an httpOnly cookie. The
 * constraint behind it is absolute: **the token is never readable from
 * client-side JavaScript**. httpOnly is what enforces that — an XSS on this
 * site cannot read the cookie, so it cannot walk away with a refresh token that
 * is valid for weeks.
 *
 * The payload is encrypted (JWE, A256GCM), not merely signed. A signed cookie
 * would be tamper-proof but readable by anyone holding the cookie file — the
 * user's own browser profile, a backup, a support screenshot. Encrypting it
 * means the tokens exist in plaintext only inside this process.
 */

export type SessionPayload = {
  accessToken: string;
  refreshToken: string;
  /** Epoch seconds. Used to decide when to refresh, never trusted for auth. */
  accessTokenExpiresAt: number;
  userId: string;
  deviceId: string;
  email: string;
};

/**
 * Lifetime of the cookie itself.
 *
 * Longer than the access token by design: the refresh token is what keeps the
 * user signed in, and rotation on every use is what limits the damage if it
 * leaks (docs/domain-model.md, `refresh_token`).
 */
export const SESSION_MAX_AGE_SECONDS = 60 * 60 * 24 * 30;

/**
 * How early a token is treated as expired.
 *
 * Without a skew, a token with four seconds left is sent, arrives expired, and
 * the request fails for no reason a user could understand.
 */
export const REFRESH_SKEW_SECONDS = 60;

const ENCRYPTION = { alg: "dir", enc: "A256GCM" } as const;

async function key(): Promise<Uint8Array> {
  // SESSION_SECRET is a passphrase of arbitrary length; A256GCM needs exactly
  // 32 bytes. SHA-256 is the derivation here — deterministic, no salt to store,
  // and the secret itself never leaves this process.
  const digest = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(sessionSecret()),
  );
  return new Uint8Array(digest);
}

export async function sealSession(payload: SessionPayload): Promise<string> {
  return new EncryptJWT({ ...payload })
    .setProtectedHeader(ENCRYPTION)
    .setIssuedAt()
    .setExpirationTime(`${SESSION_MAX_AGE_SECONDS}s`)
    .encrypt(await key());
}

/**
 * @returns the payload, or null for anything that is not a session this server
 * issued: tampered, expired, or encrypted under a rotated secret. Every one of
 * those means "not signed in", not "error" — the caller sends the visitor to the
 * sign-in page and life continues.
 */
export async function unsealSession(
  value: string | undefined,
): Promise<SessionPayload | null> {
  if (!value) return null;

  try {
    const { payload } = await jwtDecrypt(value, await key());
    const session = payload as unknown as SessionPayload;
    if (!session.accessToken || !session.refreshToken) return null;
    return session;
  } catch {
    return null;
  }
}

export function isAccessTokenStale(
  session: SessionPayload,
  nowSeconds = Math.floor(Date.now() / 1000),
): boolean {
  return session.accessTokenExpiresAt - REFRESH_SKEW_SECONDS <= nowSeconds;
}

/**
 * The cookie attributes, in one place because each one is doing a job.
 *
 * - `httpOnly` — the whole point: no script can read it.
 * - `sameSite: "lax"` — the cookie must survive a top-level navigation from an
 *   email or from the television's QR code landing on `/activate`, which
 *   `strict` would break, while still not riding along on cross-site POSTs.
 * - `secure` — configuration, not a constant, because it must be false on plain
 *   http://localhost or the browser drops the cookie silently.
 * - `path: "/"` — the proxy reads it for `/app` and `/activate` alike.
 */
export function sessionCookieOptions() {
  return {
    name: sessionCookieName(),
    httpOnly: true,
    sameSite: "lax" as const,
    secure: sessionCookieSecure(),
    path: "/",
    maxAge: SESSION_MAX_AGE_SECONDS,
  };
}
