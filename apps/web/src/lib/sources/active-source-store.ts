import "server-only";

import { cookies } from "next/headers";
import { cache } from "react";
import { api } from "@/lib/api/client";
import { fetched } from "@/lib/api/fetched";
import type { Source } from "@/lib/api/types";
import { sessionCookieSecure } from "@/lib/env";
import {
  activeSourceCookieName,
  resolveActiveSource,
  storedSourceId,
  type ActiveSource,
} from "./active-source";

/**
 * Where this browser's active source is kept, and how a page reads it (US-018).
 *
 * <h2>A cookie, and only a cookie</h2>
 *
 * The choice is per device — switching on the laptop must not move the
 * television — so it cannot be a property of the account, and it is never sent
 * to the API. It also has to be readable while rendering on the server, which
 * rules out `localStorage`, and would have needed a client component besides.
 * What is left is a cookie of this browser, one per account
 * (`lumo_active_source_<userId>`, see `activeSourceCookieName`), holding a source
 * id and nothing else.
 *
 * Not encrypted, unlike the session: it names a source, it grants nothing. The
 * API decides what the caller may read on every request, and a forged value is
 * either one of the caller's own sources — a switch they could have made from the
 * menu — or an id absent from `GET /sources`, which resolves exactly like no
 * cookie at all.
 *
 * <h2>Reads while rendering, writes in Server Actions only</h2>
 *
 * A Server Component cannot set a cookie — Next throws — and this design does
 * not need it to: `resolveActiveSource` answers from the list and the stored id
 * without ever having to "repair" the cookie. A stale value just sits there,
 * ignored, until the next explicit choice overwrites it. The write and the
 * clear below are therefore called from `src/actions/` and from nowhere else.
 *
 * `server-only` because of `cookies()`, and so that nothing here can be pulled
 * into the marketing zone by an innocent import: reading a cookie is what turns
 * a static page dynamic (`apps/web/AGENTS.md` §2).
 */

/**
 * About a year. The session cookie lasts a month and is renewed by use; this one
 * is a preference, and a preference that expires is a question asked twice.
 */
export const ACTIVE_SOURCE_MAX_AGE_SECONDS = 60 * 60 * 24 * 365;

/**
 * The session cookie's attributes, for the session cookie's reasons
 * (`lib/session/cookie.ts`): `httpOnly` because no script has any business with
 * it, `lax` so that it survives arriving from a link, `secure` from
 * configuration because it must be off on plain http://localhost.
 *
 * <h2>`path: "/"`, and why not `/app`</h2>
 *
 * The only pages that read it are under `/app`, so narrowing it there is the
 * obvious move — and it is not available: with `localePrefix: "always"` the zone
 * lives at `/fr/app` **and** `/en/app`, which share no prefix but `/`. One
 * cookie per locale path would make the active source depend on the language of
 * the page, and a user who flips to English would be asked to choose again.
 *
 * It costs the marketing zone nothing. Those pages are static: they never read
 * a cookie, so they cannot vary on one.
 */
function activeSourceCookieOptions() {
  return {
    httpOnly: true,
    sameSite: "lax" as const,
    secure: sessionCookieSecure(),
    path: "/",
    maxAge: ACTIVE_SOURCE_MAX_AGE_SECONDS,
  };
}

/** What this browser remembers for this account. Safe while rendering. */
export async function readStoredSourceId(userId: string): Promise<string | null> {
  const store = await cookies();
  return storedSourceId((name) => store.get(name)?.value, userId);
}

/**
 * Remembers a choice. **Server Actions only.**
 *
 * The caller has already established that `sourceId` is one of the account's
 * sources; this function does not know how to check that and does not pretend
 * to.
 */
export async function rememberActiveSource(
  userId: string,
  sourceId: string,
): Promise<void> {
  const name = activeSourceCookieName(userId);
  if (!name) return;

  const store = await cookies();
  store.set(name, sourceId, activeSourceCookieOptions());
}

/**
 * Forgets the choice, so that the next render decides again. **Server Actions
 * only.**
 *
 * Overwritten with an expired value rather than deleted, as `closeSession` does
 * and for its reason.
 */
export async function forgetActiveSource(userId: string): Promise<void> {
  const name = activeSourceCookieName(userId);
  if (!name) return;

  const store = await cookies();
  store.set(name, "", { ...activeSourceCookieOptions(), maxAge: 0 });
}

export type ActiveSourceView =
  /**
   * `GET /sources` did not answer. **Not** "no source", and not a reason to ask
   * for a choice: an outage proves no deletion (US-018, US-024). Whoever renders
   * this says the service is unavailable and touches nothing.
   */
  | { state: "unavailable" }
  | ({ sources: Source[] } & ActiveSource<Source>);

/**
 * The account's sources and which one is active here, once per request.
 *
 * `cache` because two things ask on the same render — the rail in the layout,
 * and a page such as Favourites — and they must get the same answer from the
 * same request rather than two lists that could disagree by one deletion. The
 * arguments are two strings, so the memoisation key is exactly "this account,
 * this token".
 *
 * `not-implemented` cannot happen for `/sources`, which has had a controller
 * since sprint 1; it is folded into `unavailable` because that is the safe side:
 * nothing is cleared and nobody is asked anything.
 */
export const loadActiveSource = cache(
  async (accessToken: string, userId: string): Promise<ActiveSourceView> => {
    const sources = await fetched(() => api(accessToken).GET("/sources", {}));
    if (sources.state !== "ok") return { state: "unavailable" };

    const items = sources.data.items;
    return { sources: items, ...resolveActiveSource(items, await readStoredSourceId(userId)) };
  },
);
