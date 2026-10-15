/**
 * Whether each home rail could be read in full, and what the page then says
 * (S11-05).
 *
 * <h2>Why per rail</h2>
 *
 * The loader reads in waves (`load-home-rails.ts`), and a rail can fail in the
 * second one: the favourites answered, but the lookup that turns their ids into
 * names did not. Reporting only the first wave, as it used to, let such a rail
 * come back empty with nothing failed — and the page then invited somebody
 * with a history to "explore", which is an outage dressed as "nothing yet".
 *
 * So each rail carries its own status. A failed rail says so in a short line
 * with a retry, beside the rails that did load; the whole page is unavailable
 * only when every rail failed and none has anything to show.
 */

/** One request a rail depends on. `skipped`: there was nothing to ask. */
export type Lookup = "ok" | "failed" | "skipped";

/**
 * `failed`: at least one request this rail depends on did not answer, so what
 * it shows — possibly nothing — is not the whole of it.
 */
export type RailStatus = "ok" | "failed";

/**
 * The outcome of one request: answered, not answered, or never asked.
 *
 * `asked` is separate from the answer because the loader's "no answer" and
 * "nothing to ask" are both `undefined`, and only the first is a failure.
 */
export function lookupOf(asked: boolean, answer: unknown): Lookup {
  if (!asked) return "skipped";
  return answer === undefined ? "failed" : "ok";
}

/** A rail is complete only when every request it depends on answered. */
export function railStatus(...lookups: readonly Lookup[]): RailStatus {
  return lookups.includes("failed") ? "failed" : "ok";
}

/**
 * A channel rail: its list (favourites, recent channels), then the lookup of
 * the channels it names. Only the ids **of this rail** count: the lookup is
 * shared by both channel rails, and a failed batch of recents must not mark
 * the favourites as incomplete.
 */
export function channelRailStatus(
  list: Lookup,
  ids: readonly string[],
  failedIds: ReadonlySet<string>,
): RailStatus {
  return railStatus(list, ids.some((id) => failedIds.has(id)) ? "failed" : "ok");
}

/**
 * What the page draws under its title, from the rails' card counts and statuses.
 *
 * - `rails` — at least one rail has cards, or only some failed: each rail
 *   draws itself, a failed one with its own line and retry.
 * - `explore` — every rail answered and every one is empty: truly nothing yet.
 * - `unavailable` — every rail failed and none has a card: one message for the
 *   page rather than three identical lines.
 */
export type HomeState = "rails" | "explore" | "unavailable";

export function homeState(rails: readonly { cards: number; status: RailStatus }[]): HomeState {
  if (rails.some((rail) => rail.cards > 0)) return "rails";
  if (rails.every((rail) => rail.status === "ok")) return "explore";
  if (rails.every((rail) => rail.status === "failed")) return "unavailable";
  return "rails";
}
