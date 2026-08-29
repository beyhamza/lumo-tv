"use server";

import { api } from "@/lib/api/client";
import { getSession } from "@/lib/session/session";

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
}): Promise<void> {
  const session = await getSession();
  if (!session) return;

  try {
    await api(session.accessToken).PUT("/me/progress", {
      body: {
        source_id: input.sourceId,
        item_type: "VOD",
        item_ref: input.filmId,
        position_ms: Math.round(input.positionMs),
        duration_ms: input.durationMs == null ? null : Math.round(input.durationMs),
      },
    });
  } catch {
    // See above: nothing to tell the user, and nothing for them to do.
  }
}
