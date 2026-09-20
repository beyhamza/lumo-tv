import "server-only";

import { api } from "@/lib/api/client";
import type { Channel, Episode, PlaybackProgress, Series, VodItem } from "@/lib/api/types";
import { resolveChannels } from "@/lib/catalogue/resolve-channels";
import { aggregateFavorites, recentChannelsOf } from "./channels";
import {
  continueCards,
  onePerSeries,
  resumableRows,
  type ContinueCard,
} from "./continue-watching";

/**
 * Everything the home page's three rails need, for one source (S8-04).
 *
 * <h2>Three waves, each as wide as it can be</h2>
 *
 * 1. What the account did: films in progress, episodes in progress, favourites,
 *    favourite groups, recent channels — five requests, in parallel.
 * 2. What those identifiers *are*: episodes, films and channels by id — in
 *    parallel. `Favorite`, `RecentChannel` and `PlaybackProgress` carry
 *    identifiers only, on purpose: a name copied onto them would be a name the
 *    next ingestion has already changed.
 * 3. The series those episodes belong to. It cannot be folded into the second
 *    wave: which series to ask for is what the second wave answers.
 *
 * So the page costs three round trips in depth whatever the account holds, and
 * never a request per card.
 *
 * <h2>A rail that fails is a rail that is absent</h2>
 *
 * The rule the channels page documents for its own rails, applied to a page made
 * of nothing else: no request here can take the screen down. A call that throws —
 * the API restarting — and a call that answers an error both leave `undefined`,
 * and the rail built on it comes out empty. What would be lost otherwise is the
 * two rails that did load.
 *
 * It is reported, though, as `failed`. A page with nothing on it because nothing
 * was ever watched invites somebody to explore; a page with nothing on it because
 * three requests failed must not say the same sentence.
 */

/**
 * Cards per rail. The number every rail of this zone already uses: a rail is a
 * reminder, not a list — and the full lists are one link away.
 */
export const HOME_RAIL_SIZE = 12;

/**
 * How many progress rows are read for each kind.
 *
 * More than a rail's worth because rows are dropped *after* they arrive —
 * finished ones, and every episode after the first of its series: somebody who
 * watched a season last week has ten rows and one card. Both stay under the
 * hundred identifiers `?ids=` accepts, so each lookup below is one request.
 */
const FILM_ROWS = HOME_RAIL_SIZE * 2;
const EPISODE_ROWS = HOME_RAIL_SIZE * 4;

/** The ceiling of `?ids=`, by contract. A longer list is a `400`. */
const IDS_MAX = 100;

export type HomeRails = {
  continueWatching: ContinueCard<PlaybackProgress, Episode>[];
  favorites: Channel[];
  recents: Channel[];
  /**
   * At least one of the first-wave requests did not answer. The group list is
   * not counted: without it the favourites keep the server's order, which is
   * the same rail.
   */
  failed: boolean;
};

export async function loadHomeRails(
  accessToken: string,
  sourceId: string,
): Promise<HomeRails> {
  const client = api(accessToken);

  const [filmRows, episodeRows, favorites, groups, recents] = await Promise.all([
    // Two requests rather than one unfiltered page, although the contract would
    // merge them for us: in one window of rows, a night of episodes pushes every
    // film out. Asked apart, each kind has its own window, and the merge is a
    // sort (`resumableRows`).
    quiet(
      client.GET("/me/progress", {
        params: { query: { sourceId, itemType: "VOD", size: FILM_ROWS } },
      }),
    ),
    quiet(
      client.GET("/me/progress", {
        params: { query: { sourceId, itemType: "EPISODE", size: EPISODE_ROWS } },
      }),
    ),
    // The whole account's: the contract has no filter by source, and the list is
    // what one person starred by hand.
    quiet(client.GET("/me/favorites", {})),
    quiet(client.GET("/me/favorite-groups", {})),
    // The maximum the contract allows, not a rail's worth: the list is the
    // account's, and the twelve most recent may all belong to another source.
    quiet(client.GET("/me/recent-channels", { params: { query: { limit: 50 } } })),
  ]);

  const rows = resumableRows(
    [...(filmRows?.items ?? []), ...(episodeRows?.items ?? [])],
    sourceId,
  );

  const starred = aggregateFavorites(
    favorites?.items ?? [],
    // `null`, not an empty list: a group list that failed must not reorder the
    // rail as if every group had been deleted. See `aggregateFavorites`.
    groups ? groups.items : null,
    sourceId,
  ).slice(0, HOME_RAIL_SIZE);
  const watched = recentChannelsOf(recents?.items ?? [], sourceId, HOME_RAIL_SIZE);

  const episodeIds = unique(
    rows.filter((row) => row.item_type === "EPISODE").map((row) => row.item_ref),
  ).slice(0, IDS_MAX);
  // The first films only: a rail of twelve holds at most twelve of them, and the
  // margin is for the ones the last re-synchronisation dropped.
  const filmIds = unique(
    rows.filter((row) => row.item_type === "VOD").map((row) => row.item_ref),
  ).slice(0, FILM_ROWS);

  const [episodes, films, channelsById] = await Promise.all([
    episodeIds.length > 0
      ? quiet(
          client.GET("/sources/{id}/episodes", {
            params: { path: { id: sourceId }, query: { ids: episodeIds, size: episodeIds.length } },
          }),
        )
      : undefined,
    filmIds.length > 0
      ? quiet(
          client.GET("/sources/{id}/vod", {
            // `size` explicitly: it defaults to 50 and a longer list of ids would
            // come back half answered with a `200`.
            params: { path: { id: sourceId }, query: { ids: filmIds, size: filmIds.length } },
          }),
        )
      : undefined,
    // One lookup for both channel rails: a favourite watched this morning is in
    // both, and `resolveChannels` asks for it once. It never throws.
    resolveChannels(
      [...starred, ...watched].map((entry) => ({ sourceId, channelId: entry.channel_id })),
      async (id, ids, size) => {
        const answer = await client.GET("/sources/{id}/channels", {
          params: { path: { id }, query: { ids, size } },
        });
        return answer.data?.items ?? [];
      },
    ),
  ]);

  const candidates = onePerSeries(rows, byId<Episode>(episodes?.items));

  const seriesIds = unique(
    candidates.flatMap((candidate) =>
      candidate.kind === "episode" ? [candidate.episode.series_id] : [],
    ),
  ).slice(0, HOME_RAIL_SIZE * 2);
  const series =
    seriesIds.length > 0
      ? await quiet(
          client.GET("/sources/{id}/series", {
            params: { path: { id: sourceId }, query: { ids: seriesIds, size: seriesIds.length } },
          }),
        )
      : undefined;

  // Walked against the account's order, never the answers': the lookups come
  // back in the catalogue's order, and these lists are the user's. A channel the
  // last ingestion dropped is absent from the map and falls out.
  const named = (entries: readonly { channel_id: string }[]) =>
    entries
      .map((entry) => channelsById.get(entry.channel_id))
      .filter((channel): channel is Channel => channel !== undefined);

  return {
    continueWatching: continueCards(
      candidates,
      byId<VodItem>(films?.items),
      byId<Series>(series?.items),
      HOME_RAIL_SIZE,
    ),
    favorites: named(starred),
    recents: named(watched),
    failed: [filmRows, episodeRows, favorites, recents].some((answer) => answer === undefined),
  };
}

/**
 * The body of an answer, or `undefined` — for a refusal and for no answer at all.
 *
 * `openapi-fetch` *throws* when the connection fails and *returns* an error when
 * the API refuses; inside a `Promise.all` the first would reject the whole wave
 * and with it the page. A rail does not get to do that.
 */
async function quiet<T>(call: Promise<{ data?: T }>): Promise<T | undefined> {
  try {
    return (await call).data;
  } catch {
    return undefined;
  }
}

function byId<T extends { id: string }>(items: readonly T[] | undefined): Map<string, T> {
  return new Map((items ?? []).map((item) => [item.id, item]));
}

function unique(values: readonly string[]): string[] {
  return [...new Set(values)];
}
