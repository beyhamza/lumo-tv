import { aggregateFavorites } from "@/lib/home/channels";
import { membershipsOf, type MembershipRef } from "./global-removal";

/**
 * What `/app/favorites` lists, as a decision rather than markup (S11-02, S11-03).
 *
 * <h2>Two views, two different lists</h2>
 *
 * - **All favourites** is the US-020 aggregation, the same as the home rail:
 *   each channel **once**, groups in their order then channels in theirs, the
 *   first occurrence deciding the place. A channel filed in two groups used to
 *   be listed twice here, and its "Remove" took out one membership out of two —
 *   which looked like a remove that did not work. Each row now carries every
 *   membership of its channel, which is what "Remove from all my favourites"
 *   and "Organise" act on.
 * - **A group** is that group's raw rows for the active source, in the group's
 *   order: the list the move buttons act on, one membership per row.
 *
 * A group named in the URL that no longer exists — deleted from the phone, or a
 * stale bookmark — falls back to All favourites rather than to an empty list
 * that looks like a bug, as on the channels page.
 */
export type FavoritesView<F extends MembershipRef> =
  | { kind: "all"; rows: { favorite: F; memberships: F[] }[] }
  | { kind: "group"; groupId: string; rows: F[] };

export function favoritesView<F extends MembershipRef>(
  favorites: readonly F[],
  groups: readonly { id: string; position: number }[],
  sourceId: string,
  groupId: string | undefined,
): FavoritesView<F> {
  if (groupId && groups.some((group) => group.id === groupId)) {
    const rows = favorites
      .filter((favorite) => favorite.source_id === sourceId && favorite.group_id === groupId)
      .sort((a, b) => a.position - b.position);
    return { kind: "group", groupId, rows };
  }

  return {
    kind: "all",
    rows: aggregateFavorites(favorites, groups, sourceId).map((favorite) => ({
      favorite,
      memberships: membershipsOf(favorites, groups, sourceId, favorite.channel_id),
    })),
  };
}
