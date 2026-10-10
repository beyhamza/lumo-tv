import "server-only";

import { cookies } from "next/headers";
import { sessionCookieName } from "@/lib/env";
import {
  isAccessTokenStale,
  sealSession,
  sessionCookieOptions,
  unsealSession,
  type SessionPayload,
} from "./cookie";
import { refreshSession } from "./refresh";

/**
 * The session in a Route Handler under `/api`, refreshed when it is stale.
 *
 * `proxy.ts` refreshes before a page renders, but its matcher leaves `/api`
 * out — a redirect to sign-in there would hand a `fetch` an HTML page. So the
 * handlers used a token nobody refreshed: a page left open past the access
 * token's lifetime answered 401 on *Play* while the refresh token was still
 * good (S10B-05). A Route Handler, unlike a Server Component, may set a cookie,
 * so it does the proxy's work itself:
 *
 * - **rotated** — the new cookie goes out with the response, and the new token
 *   is used for the call;
 * - **rejected** — the cookie is cleared and the handler answers 401, never a
 *   redirect;
 * - **unavailable** — the session is kept as it is; the call will say what the
 *   API says.
 */
export async function getRouteSession(): Promise<SessionPayload | null> {
  const store = await cookies();
  const session = await unsealSession(store.get(sessionCookieName())?.value);
  if (!session) return null;
  if (!isAccessTokenStale(session)) return session;

  const result = await refreshSession(session);
  switch (result.status) {
    case "rotated": {
      const options = sessionCookieOptions();
      store.set(options.name, await sealSession(result.session), options);
      return result.session;
    }
    case "rejected": {
      const { name, ...options } = sessionCookieOptions();
      store.set(name, "", { ...options, maxAge: 0 });
      return null;
    }
    case "unavailable":
      return session;
  }
}
