import type { EpgChannelProgrammes, EpgProgramme } from "@/lib/api/types";

/**
 * "On now" and "up next", from a channel's programmes and a clock (S9-03,
 * after S7-03).
 *
 * <h2>Computed, not asked</h2>
 *
 * The server returns every programme overlapping the window with its full
 * times; which one is on *now* is a comparison of instants and nothing else
 * (sprint 9 framing: "se calcule depuis les horaires et l'horloge locale, sans
 * requête à la seconde"). Doing it here means one request covers a rail, a
 * page, or a grid, whatever it draws.
 *
 * <h2>Instants, never local hours</h2>
 *
 * `starts_at` and `ends_at` are RFC 3339 with an offset and are compared as
 * epoch milliseconds. Midnight is not special and a clock change is not
 * special: two identical wall-clock times can be two different instants
 * (docs/design/0.2.0/guide-interactions.md), which is exactly the bug that
 * comparing "20:00" to "20:00" would ship.
 *
 * <h2>What "next" means, and what it does not</h2>
 *
 * The first programme that starts strictly after now. In a gap — the current
 * one ended, the next has not begun — there is no `current` and `next` is
 * what is coming. When a provider's listing overlaps two programmes, `next`
 * may start before `current` ends; that is the listing's claim, echoed, not
 * corrected here.
 */

/** The minimum a caller has to hand over; `EpgProgramme` satisfies it. */
export type Timed = Pick<EpgProgramme, "id" | "starts_at" | "ends_at">;

export type Airing<P extends Timed> = {
  current: P | undefined;
  next: P | undefined;
};

export function currentAndNext<P extends Timed>(
  programmes: readonly P[],
  now: Date,
): Airing<P> {
  const at = now.getTime();

  // Sorted defensively although the contract already orders by `starts_at`
  // then `id`: after a client-side merge this is the same order restored, and
  // a caller with programmes from anywhere else gets the same answer.
  const timed = programmes
    .map((programme) => ({
      programme,
      starts: Date.parse(programme.starts_at),
      ends: Date.parse(programme.ends_at),
    }))
    // An unreadable time is a programme that cannot be placed, so it is not
    // placed. It is not a reason to place nothing.
    .filter((entry) => !Number.isNaN(entry.starts) && !Number.isNaN(entry.ends))
    .sort((a, b) => a.starts - b.starts || a.programme.id.localeCompare(b.programme.id));

  // `starts_at` inclusive, `ends_at` exclusive — the same convention as the
  // window itself, so a programme is never "on" at the instant it ends and the
  // one that follows is.
  const current = timed.find((entry) => entry.starts <= at && at < entry.ends)?.programme;
  const next = timed.find((entry) => entry.starts > at)?.programme;

  return { current, next };
}

/**
 * The programme on air for each channel of a grid answer, by channel id. A
 * channel with nothing on — no guide, no `tvg_id`, or a gap — is absent from
 * the map, which is what lets a card render nothing for it (S7-03).
 */
export function onAirByChannel(
  channels: readonly EpgChannelProgrammes[],
  now: Date,
): Map<string, EpgProgramme> {
  const out = new Map<string, EpgProgramme>();
  for (const row of channels) {
    const { current } = currentAndNext(row.programmes, now);
    if (current) out.set(row.channel_id, current);
  }
  return out;
}
