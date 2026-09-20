import type { Favorite, FavoriteGroup, RecentChannel } from "@/lib/api/types";

/**
 * The two channel rails of the home page, as decisions rather than markup
 * (US-020, S8-04).
 *
 * Pure on purpose. Both rules below are wrong in ways nobody sees: a channel
 * drawn twice looks like a generous rail, and a rail ordered by the wrong key
 * looks like a rail. The page fetches; this file decides; the tests hold the
 * decisions.
 */

/** Only what the ordering needs, so a test does not have to invent the rest. */
type FavoriteRef = Pick<Favorite, "id" | "group_id" | "source_id" | "channel_id" | "position">;
type GroupRef = Pick<FavoriteGroup, "id" | "position">;
type RecentRef = Pick<RecentChannel, "channel_id" | "source_id">;

/**
 * Every favourite of one source, each channel **once**, in the user's order.
 *
 * <h2>The rule, as US-020 words it</h2>
 *
 * Walk the groups in their order, then the channels in their order inside each
 * group; the first occurrence of a channel decides its place. No ranking of its
 * own is kept for the home page — reorder a group in the library and this rail
 * follows.
 *
 * <h2>Identity is the channel id, never the name</h2>
 *
 * A channel filed under two groups is two `Favorite` rows with one `channel_id`.
 * Two *different* channels that happen to share a display name — a provider's SD
 * and HD feeds often do — are two channels and both stay.
 *
 * <h2>Nothing is removed by this</h2>
 *
 * It is a presentation. The memberships are untouched, and the row returned for
 * a channel is its first one — so if that membership is later removed, the next
 * remaining one takes over its place, which is the rule of 19 September 2026 for
 * a partially failed removal.
 *
 * @param groups the account's groups, or `null` when `GET /me/favorite-groups`
 *   did not answer. The favourites are then walked in the order the server gave
 *   them, which the contract states is "by group then position" already — a
 *   lost group list degrades to the same rail, not to an empty one.
 */
export function aggregateFavorites<F extends FavoriteRef>(
  favorites: readonly F[],
  groups: readonly GroupRef[] | null,
  sourceId: string,
): F[] {
  const ofSource = favorites.filter((favorite) => favorite.source_id === sourceId);

  const ordered = groups === null ? ofSource : byGroupThenPosition(ofSource, groups);

  const seen = new Set<string>();
  const out: F[] = [];
  for (const favorite of ordered) {
    if (seen.has(favorite.channel_id)) continue;
    seen.add(favorite.channel_id);
    out.push(favorite);
  }
  return out;
}

/**
 * Sorted explicitly rather than trusted, because two lists are involved and they
 * come from two requests: the order of `GET /me/favorites` is a promise about
 * *that* answer, and a group moved between the two reads would otherwise produce
 * a rail that matches neither.
 *
 * A favourite whose group is not in the list — created on another device between
 * the two requests — goes after the known groups instead of vanishing. `sort` is
 * stable, so such rows keep the server's order among themselves.
 */
function byGroupThenPosition<F extends FavoriteRef>(
  favorites: readonly F[],
  groups: readonly GroupRef[],
): F[] {
  const rank = new Map(
    [...groups]
      .sort((a, b) => a.position - b.position)
      .map((group, index) => [group.id, index] as const),
  );
  const rankOf = (favorite: F) => rank.get(favorite.group_id) ?? Number.MAX_SAFE_INTEGER;

  return [...favorites].sort(
    (a, b) => rankOf(a) - rankOf(b) || a.position - b.position,
  );
}

/**
 * The recently watched channels of one source, newest first, at most `limit`.
 *
 * `GET /me/recent-channels` answers for the whole account and has no source
 * filter, so the filter is here. The order is the server's and is kept: it is
 * "most recent first" by contract, which is the only order this rail has.
 *
 * Deduplicated by channel id as a guard rather than out of need — the server
 * keeps one row per channel — because a rail with the same card twice is the
 * kind of defect that costs nothing to make impossible.
 */
export function recentChannelsOf<R extends RecentRef>(
  recents: readonly R[],
  sourceId: string,
  limit: number,
): R[] {
  const seen = new Set<string>();
  const out: R[] = [];
  for (const recent of recents) {
    if (recent.source_id !== sourceId || seen.has(recent.channel_id)) continue;
    seen.add(recent.channel_id);
    out.push(recent);
    if (out.length === limit) break;
  }
  return out;
}
