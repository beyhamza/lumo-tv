import "server-only";

import { DIRECT_VIEW_COOKIE_PREFIX } from "@/lib/direct/view-memory";
import { sessionCookieSecure } from "@/lib/env";
import { ACTIVE_SOURCE_COOKIE_PREFIX } from "@/lib/sources/active-source";

/**
 * The cookies that belong to an account rather than to the browser, removed
 * whenever its session ends (`BUG-R020-01-02`, release lock `R020-01`).
 *
 * Signing out cleared the session cookie and nothing else. Left behind, for a
 * year, were `lumo_active_source_<userId>` — whose very name is the account's
 * identifier — and one `lumo_direct_view_<sourceId>` per source browsed. The next
 * account on the same browser never reads them, but "nothing of A left once B is
 * here" is the criterion, and the Android clients purge their equivalents too
 * (`BUG-R020-01-01`).
 *
 * Matched by prefix over the cookies the request carries: the names are keyed by
 * identifiers this side has no list of once the session is gone.
 */
const ACCOUNT_COOKIE_PREFIXES = [ACTIVE_SOURCE_COOKIE_PREFIX, DIRECT_VIEW_COOKIE_PREFIX];

export function accountCookieNames(names: Iterable<string>): string[] {
  return [...names].filter((name) =>
    ACCOUNT_COOKIE_PREFIXES.some((prefix) => name.startsWith(prefix)),
  );
}

type CookieSetter = (
  name: string,
  value: string,
  options: {
    httpOnly: boolean;
    sameSite: "lax";
    secure: boolean;
    path: string;
    maxAge: number;
  },
) => void;

/**
 * Overwrites each account cookie with an expired one, as `closeSession` does
 * for the session: same path as when written (`/`), or the browser keeps it.
 *
 * `set` is `cookies().set` in a Server Action or Route Handler and
 * `response.cookies.set` in the proxy — the two places a session ends.
 */
export function expireAccountCookies(names: Iterable<string>, set: CookieSetter): void {
  for (const name of accountCookieNames(names)) {
    set(name, "", {
      httpOnly: true,
      sameSite: "lax",
      secure: sessionCookieSecure(),
      path: "/",
      maxAge: 0,
    });
  }
}
