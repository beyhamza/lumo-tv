import type { Episode, PlaybackProgress, Series, VodItem } from "@/lib/api/types";
import { isFinished } from "@/lib/playback/progress";

/**
 * The "Continue" rail of the home page, as three pure steps (US-017, US-019,
 * S8-04).
 *
 * <h2>Why three steps and not one function</h2>
 *
 * `GET /me/progress` carries identifiers and positions, nothing else. Between
 * "which rows are worth a card" and "what the card shows" sit two lookups the
 * page has to make — episodes by id, to learn which **series** a row belongs to,
 * then films and series by id, for a title and a poster. Each step below is the
 * decision made between two of those requests, so the page holds no rule of its
 * own: it fetches, and hands over.
 *
 * <h2>What this rail is not, yet</h2>
 *
 * The decisions of 19 September 2026 keep a series in Continue when its last
 * episode is finished **and a next one exists**, proposing that next one. Knowing
 * what follows an episode takes the series' tree — one call per series against
 * the user's own panel — and the full resume rules are sprint 12. Until then a
 * finished row is simply not a candidate, and a series whose only rows are
 * finished leaves the rail. Removing a card by hand is sprint 12 as well: there
 * is no control for it here, rather than a control that does nothing.
 */

type ProgressRow = Pick<
  PlaybackProgress,
  "source_id" | "item_type" | "item_ref" | "position_ms" | "duration_ms" | "updated_at"
>;
type EpisodeRef = Pick<Episode, "id" | "series_id" | "season_number" | "episode_number">;

/**
 * Step one: the rows worth a card, most recently updated first.
 *
 * - **This source only.** Progress is keyed by source, and US-018 says Continue
 *   shows the active source and nothing else.
 * - **Finished rows are out** — `isFinished`, the 95 % threshold every other
 *   rail already uses, and with it the rule that an unknown duration is never
 *   finished: a film that lingers is an annoyance, one that vanishes is a loss.
 * - **Sorted here**, although each answer of the API is sorted already: the page
 *   asks for films and episodes separately — so that a night of episodes cannot
 *   push every film out of the window — and two sorted lists concatenated are not
 *   a sorted list.
 *
 * A timestamp that does not parse sorts last instead of throwing: one odd row
 * must not cost the rail.
 */
export function resumableRows<R extends ProgressRow>(
  rows: readonly R[],
  sourceId: string,
): R[] {
  const at = (row: R) => {
    const parsed = Date.parse(row.updated_at);
    return Number.isNaN(parsed) ? Number.NEGATIVE_INFINITY : parsed;
  };

  return rows
    .filter((row) => row.source_id === sourceId)
    .filter((row) => !isFinished(row.position_ms, row.duration_ms ?? null))
    .sort((a, b) => at(b) - at(a));
}

/** A row that survived step two, with what is known about it so far. */
export type ContinueCandidate<R extends ProgressRow, E extends EpisodeRef> =
  | { kind: "film"; row: R }
  | { kind: "episode"; row: R; episode: E };

/**
 * Step two: **one card per series**, never one per episode.
 *
 * Somebody who started three episodes last night has three rows and wants one
 * card. The rows arrive most recent first, so the first row met for a series is
 * its most recent episode in progress, and every later one is skipped.
 *
 * An episode the resolver did not return was dropped by the last
 * re-synchronisation: absent by contract, so it falls out rather than rendering
 * as a gap — and it does **not** claim its series' slot, since the series it
 * belonged to is precisely what is no longer known.
 *
 * Films pass through untouched. Their position in the list is preserved, which
 * is what keeps the merge order of step one.
 */
export function onePerSeries<R extends ProgressRow, E extends EpisodeRef>(
  rows: readonly R[],
  episodesById: ReadonlyMap<string, E>,
): ContinueCandidate<R, E>[] {
  const seen = new Set<string>();
  const out: ContinueCandidate<R, E>[] = [];

  for (const row of rows) {
    if (row.item_type === "VOD") {
      out.push({ kind: "film", row });
      continue;
    }
    const episode = episodesById.get(row.item_ref);
    if (!episode || seen.has(episode.series_id)) continue;
    seen.add(episode.series_id);
    out.push({ kind: "episode", row, episode });
  }
  return out;
}

/** What the rail draws. */
export type ContinueCard<R extends ProgressRow, E extends EpisodeRef> =
  | { kind: "film"; key: string; row: R; film: VodItem }
  | { kind: "episode"; key: string; row: R; episode: E; series: Series };

/**
 * Step three: titles and posters, then the cap.
 *
 * **The cap comes last.** A film or a series the last re-synchronisation dropped
 * is absent from the lookups and falls out here; cutting to twelve before that
 * would leave a rail of nine for no reason anybody could see.
 */
export function continueCards<R extends ProgressRow, E extends EpisodeRef>(
  candidates: readonly ContinueCandidate<R, E>[],
  filmsById: ReadonlyMap<string, VodItem>,
  seriesById: ReadonlyMap<string, Series>,
  limit: number,
): ContinueCard<R, E>[] {
  const out: ContinueCard<R, E>[] = [];

  for (const candidate of candidates) {
    if (out.length === limit) break;

    if (candidate.kind === "film") {
      const film = filmsById.get(candidate.row.item_ref);
      if (film) out.push({ kind: "film", key: `film:${film.id}`, row: candidate.row, film });
      continue;
    }

    const series = seriesById.get(candidate.episode.series_id);
    if (series) {
      out.push({
        kind: "episode",
        key: `series:${series.id}`,
        row: candidate.row,
        episode: candidate.episode,
        series,
      });
    }
  }
  return out;
}
