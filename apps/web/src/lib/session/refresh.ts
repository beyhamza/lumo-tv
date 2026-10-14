import "server-only";

import { apiBaseUrl } from "@/lib/env";
import type { RefreshRequest, TokenPair } from "@/lib/api/types";
import type { SessionPayload } from "./cookie";

/**
 * Server-side token refresh.
 *
 * Written against `fetch` rather than the generated client for one reason: this
 * runs in `proxy.ts`, which executes before the application and is expected to
 * stay free of shared modules. A bare fetch keeps it self-contained.
 *
 * The status mapping is the part that matters:
 *
 * - **401 / 409** — the refresh token is expired, revoked or already used. On
 *   reuse the server has just revoked this device's entire chain
 *   (docs/domain-model.md, `refresh_token`); there is nothing to retry with, so
 *   the session is dropped and the visitor signs in again.
 * - **anything else, including no response** — unknown. The session is kept.
 *   Signing a user out because the API restarted is the failure this
 *   distinction exists to prevent.
 */
export type RefreshResult =
  | { status: "rotated"; session: SessionPayload }
  | { status: "rejected" }
  | { status: "unavailable" };

/**
 * A refresh already in flight, keyed by the refresh token being spent.
 *
 * Two requests arriving together — a navigation and its RSC prefetch — would
 * otherwise both refresh, and the second would present a token the first has
 * already rotated. The server would read that as theft and revoke the chain,
 * signing the user out for clicking a link (the same trap US-04 describes for
 * the mobile client).
 *
 * This deduplicates within one server process, which covers local development
 * and a single instance. It does NOT cover several instances behind a load
 * balancer: that needs a shared lock, and it is a deployment decision rather
 * than one to bake in now. The skew above keeps the window small in the
 * meantime.
 */
const inFlight = new Map<string, Promise<RefreshResult>>();

/**
 * How long a finished rotation is handed again to a request still carrying the
 * token it spent.
 *
 * Deduplicating only what is in flight was not enough (S10B-05). A prefetch or
 * a second tab that left with the old cookie a moment before the rotation, and
 * arrives a moment after it, found nothing in flight, spent the old token again
 * — and the server, seeing a reuse, revoked the whole chain. Within this window
 * that request gets the same new pair instead.
 *
 * Short on purpose: the old token is a credential, and for these seconds this
 * process will exchange it without the server's reuse check seeing it. The
 * races this covers are a page and its requests, measured in milliseconds to a
 * couple of seconds.
 */
export const ROTATION_GRACE_MS = 15_000;

const recentlyRotated = new Map<string, { result: RefreshResult; until: number }>();

export function refreshSession(
  session: SessionPayload,
  now: () => number = Date.now,
): Promise<RefreshResult> {
  forgetExpired(now());

  const rotated = recentlyRotated.get(session.refreshToken);
  if (rotated) return Promise.resolve(rotated.result);

  const existing = inFlight.get(session.refreshToken);
  if (existing) return existing;

  const attempt = performRefresh(session)
    .then((result) => {
      if (result.status === "rotated") {
        recentlyRotated.set(session.refreshToken, {
          result,
          until: now() + ROTATION_GRACE_MS,
        });
      }
      return result;
    })
    .finally(() => {
      inFlight.delete(session.refreshToken);
    });

  inFlight.set(session.refreshToken, attempt);
  return attempt;
}

/** Bounded by traffic: entries older than the window go on the next call. */
function forgetExpired(nowMs: number): void {
  for (const [token, entry] of recentlyRotated) {
    if (entry.until <= nowMs) recentlyRotated.delete(token);
  }
}

async function performRefresh(
  session: SessionPayload,
): Promise<RefreshResult> {
  let response: Response;

  try {
    response = await fetch(`${apiBaseUrl()}/auth/refresh`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ refresh_token: session.refreshToken } satisfies RefreshRequest),
      // Never cached: the response contains a single-use token, and a cached
      // rotation would replay a token the server has already retired.
      cache: "no-store",
    });
  } catch {
    return { status: "unavailable" };
  }

  if (response.status === 401 || response.status === 409) {
    return { status: "rejected" };
  }

  if (!response.ok) {
    return { status: "unavailable" };
  }

  // Typed from the contract even though the call is a bare fetch: the reason
  // for the bare fetch is that this runs in the proxy, not that the response
  // shape is anyone's guess.
  const body = (await response.json()) as TokenPair;

  return {
    status: "rotated",
    session: {
      ...session,
      accessToken: body.access_token,
      refreshToken: body.refresh_token,
      accessTokenExpiresAt: Math.floor(Date.now() / 1000) + body.expires_in,
    },
  };
}
