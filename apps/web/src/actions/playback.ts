"use server";

import { api } from "@/lib/api/client";
import { type SaveOutcome, saveOutcomeOf } from "@/lib/playback/save-outcome";
import { sanitizeWatchExtras, type WatchedSegment } from "@/lib/playback/watch-meter";
import { getSession } from "@/lib/session/session";

/**
 * What a player measured since its previous save (S12-02, `adr/0012`): the
 * intervals of actual playback, when the position was read, and when this page
 * session really started playing. Re-checked before sending — a Server Action
 * is a public endpoint, and what arrives is whatever a browser posted.
 */
type WatchExtras = {
  watchedSegments?: WatchedSegment[];
  observedAt?: string;
  sessionStartedAt?: string | null;
};

/**
 * Records that a channel was actually watched.
 *
 * <p>Sent when playback starts, never when a channel is focused or hovered. The
 * contract is explicit about it, and the reason is not tidiness: a "recently
 * watched" rail built from what a cursor passed over on its way somewhere is
 * noise, and it is the user's own history being made worse.
 *
 * <p>Deliberately silent on failure. This is a side effect of watching
 * television, not a step of it — a rail that misses an entry is a smaller
 * problem than a player that interrupts itself to report one.
 */
export async function recordWatched(channelId: string): Promise<void> {
  const session = await getSession();
  if (!session) return;

  try {
    await api(session.accessToken).PUT("/me/recent-channels", {
      body: { channel_id: channelId },
    });
  } catch {
    // See above: nothing to tell the user, and nothing for them to do.
  }
}

/**
 * Saves where somebody stopped watching a film (S5-11).
 *
 * <p>Called every thirty seconds while playing, on pause, and on the way out —
 * never per frame. `PUT /me/progress` is an idempotent upsert, not a stream, and
 * thirty seconds is the trade between losing a minute when a tab is closed
 * abruptly and writing two thousand times a film.
 *
 * <p><b>`itemRef` is the film's own id.</b> Decided in `S5-11` and written into
 * the contract: a "continue watching" rail has to turn these rows back into
 * films with posters, and `GET /sources/{id}/vod?ids=` is the only operation
 * that does.
 *
 * <p><b>Never called for a live channel.</b> `ProgressItemType` has no `LIVE`
 * value, so it cannot even be expressed — a channel gets `recordWatched` above.
 * The guard that matters is on the caller's side, where a shared player could
 * otherwise report a position for a continuous stream; see
 * `lib/playback/progress.ts`.
 *
 * <p>Silent on failure, for the reason `recordWatched` is: this is bookkeeping
 * around watching a film, not a step of it.
 */
export async function saveFilmProgress(input: {
  sourceId: string;
  filmId: string;
  positionMs: number;
  durationMs: number | null;
} & WatchExtras): Promise<SaveOutcome> {
  return save(input, "VOD", input.filmId);
}

/**
 * Saves where somebody stopped watching an episode (S6-08).
 *
 * <p>[saveFilmProgress] with `EPISODE` in place of `VOD`, and that is the whole
 * difference: the server stores `item_ref` opaquely and never asks what it points
 * at, so this needed nothing new behind it.
 *
 * <p><b>Two actions rather than one with an `itemType` argument.</b> The two ids come
 * from two different tables, and a caller that could pass either would eventually
 * file a film under `EPISODE` — a row nothing would ever read again. The contract
 * makes the same argument about `item_ref` itself.
 *
 * <p>What reads these rows is a rail of <i>series</i>: progress is recorded on an
 * episode, resuming is thought about in series, and the turn between the two needs
 * the tree. Nothing here knows about that, and it must not.
 *
 * <p>Silent on failure, for the reason the two above are.
 */
export async function saveEpisodeProgress(input: {
  sourceId: string;
  episodeId: string;
  positionMs: number;
  durationMs: number | null;
} & WatchExtras): Promise<SaveOutcome> {
  return save(input, "EPISODE", input.episodeId);
}

/**
 * The one `PUT /me/progress` both actions share. Not exported: a `"use server"`
 * module's exports are endpoints, and this one would take any item type.
 *
 * <p>What it answers is for the player's own bookkeeping only — whether the
 * watched intervals it carried must be sent again (S12-02). The user is told
 * nothing either way, for the reason `recordWatched` gives.
 */
async function save(
  input: { sourceId: string; positionMs: number; durationMs: number | null } & WatchExtras,
  itemType: "VOD" | "EPISODE",
  itemRef: string,
): Promise<SaveOutcome> {
  const session = await getSession();
  if (!session) return "drop";

  try {
    const { response } = await api(session.accessToken).PUT("/me/progress", {
      body: {
        source_id: input.sourceId,
        item_type: itemType,
        item_ref: itemRef,
        position_ms: Math.round(input.positionMs),
        duration_ms: input.durationMs == null ? null : Math.round(input.durationMs),
        ...sanitizeWatchExtras(input),
      },
    });
    return saveOutcomeOf(response.status);
  } catch {
    // No answer at all. The intervals are kept and resent with the next save —
    // a resent interval adds nothing on the server (CW-33).
    return "retry";
  }
}
