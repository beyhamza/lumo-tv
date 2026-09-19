/**
 * Which source this browser is browsing (US-018, S8-03).
 *
 * <h2>Why there is a decision to make at all</h2>
 *
 * An account can hold several sources, and a catalogue, a home or a list of
 * favourites that mixes them is unreadable. So one source is "active" — **per
 * device, never per account**: switching on the laptop must not move the
 * television in the living room. That is why the choice lives in a cookie of
 * this browser and is never sent to the API (`active-source-store.ts`).
 *
 * <h2>Why this file imports nothing</h2>
 *
 * The rules below are the part that is invisible in review and expensive when
 * wrong — a source picked silently among several, a choice wiped by an outage —
 * so they are plain functions over plain values and ship with their tests
 * (AGENTS.md §5). Everything that touches `cookies()` is next door, behind
 * `server-only`.
 */

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * The shape of every identifier the contract hands out.
 *
 * Both halves of the cookie — the account id in its name, the source id in its
 * value — come back from the browser, which is to say from anybody. Neither is
 * used for anything before it has passed this.
 */
export function isUuid(value: unknown): value is string {
  return typeof value === "string" && UUID.test(value);
}

export const ACTIVE_SOURCE_COOKIE_PREFIX = "lumo_active_source_";

/**
 * One cookie per account: `lumo_active_source_<userId>`.
 *
 * Scoped by account because a browser is not a person: two people sharing a
 * laptop each sign in to their own account, and the second must not inherit a
 * choice that names a source they do not even own. A suffix rather than a JSON
 * map in one cookie because there is then nothing to parse — a value is a UUID or
 * it is nothing — and signing out of one account cannot corrupt the other's.
 *
 * @returns null when the account id is not a UUID. That never happens with a
 * session this server issued; it is refused anyway so that a cookie *name* is
 * never built from an unchecked string.
 */
export function activeSourceCookieName(userId: string): string | null {
  return isUuid(userId) ? `${ACTIVE_SOURCE_COOKIE_PREFIX}${userId.toLowerCase()}` : null;
}

/**
 * The source id this browser remembers for this account, or null.
 *
 * `read` is `cookies().get(name)?.value` in production and a `Map` in the tests.
 * Anything that is not a UUID reads as "nothing stored": a hand-edited cookie
 * gets the same treatment as no cookie, and never reaches a URL or a comparison.
 */
export function storedSourceId(
  read: (name: string) => string | undefined,
  userId: string,
): string | null {
  const name = activeSourceCookieName(userId);
  if (!name) return null;

  const value = read(name);
  return isUuid(value) ? value : null;
}

export type ActiveSource<T> =
  /** The account has no source: the application proposes adding one. */
  | { state: "none" }
  | { state: "selected"; source: T }
  /** Several sources and no valid memory of one: the user is asked. */
  | { state: "needs-choice" };

/**
 * Decides the active source from a list of sources that was **successfully
 * fetched** and what this browser remembers.
 *
 * - no source → `none`;
 * - the remembered one is still there → it;
 * - nothing valid remembered, exactly one source → that one. There is nothing to
 *   choose, and asking would be a question with one answer;
 * - nothing valid remembered, several sources → `needs-choice`. **Never the
 *   first of the list**: the user would be browsing a catalogue they did not
 *   pick, under a name they may not notice, and every favourite "missing" from
 *   it would look like data loss.
 *
 * The same function covers a deletion made on another device (US-018, US-024):
 * the remembered id is simply absent from the list, and the last two rules
 * apply. It needs no write — which matters, because it runs during rendering,
 * where a cookie cannot be set.
 *
 * <h2>What must never be passed in</h2>
 *
 * An empty list standing in for a failed request. "The API did not answer" and
 * "the account has no source" are different facts, and an outage proves no
 * deletion: the caller renders its unavailable state and leaves the stored
 * choice alone. The signature cannot enforce that; `loadActiveSource` does.
 */
export function resolveActiveSource<T extends { id: string }>(
  sources: readonly T[],
  storedId: string | null,
): ActiveSource<T> {
  if (sources.length === 0) return { state: "none" };

  const remembered = storedId
    ? sources.find((source) => source.id.toLowerCase() === storedId.toLowerCase())
    : undefined;
  if (remembered) return { state: "selected", source: remembered };

  if (sources.length === 1) return { state: "selected", source: sources[0] };

  return { state: "needs-choice" };
}
