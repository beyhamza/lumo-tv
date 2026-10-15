import type { Episode, PlaybackProgress, ProgressItemType, Series, VodItem } from "@/lib/api/types";
import { isFinished } from "@/lib/playback/progress";
import { nextAfter } from "@/lib/playback/sheet-action";

/**
 * The "Continue" rail of the home page, as pure steps (US-017, US-019, S8-04,
 * S12-04).
 *
 * <h2>The server decides what belongs; this decides what a card shows</h2>
 *
 * Since S12-01 every row says whether it is in Continue: `in_continue`, computed
 * once on the server from 30 seconds actually watched across devices, the 95 %
 * line and a removal by the user (`adr/0012`, ruling 4). Nothing here re-derives
 * those rules. What stays on this side is the part only a client can do: turn
 * episodes into **one card per series**, and — when the latest episode of a
 * series is finished — propose the next one (CW-08, CW-19, CW-21), which takes
 * the series' tree.
 *
 * <h2>Why steps and not one function</h2>
 *
 * `GET /me/progress` carries identifiers, nothing else. Between "which rows are
 * worth a card" and "what the card shows" sit lookups the page has to make —
 * episodes by id, to learn which series a row belongs to; the tree of a series
 * whose latest episode is finished, to learn what comes next; films and series
 * by id, for a title and a poster. Each step below is the decision made between
 * two of those requests, so the page holds no rule of its own.
 */

type ProgressRow = Pick<
  PlaybackProgress,
  | "source_id"
  | "item_type"
  | "item_ref"
  | "position_ms"
  | "duration_ms"
  | "updated_at"
  | "hidden"
  | "in_continue"
>;
type EpisodeRef = Pick<Episode, "id" | "series_id" | "season_number" | "episode_number">;

/**
 * Step one: the rows that can make a card, most recently updated first.
 *
 * - **This source only.** US-018: Continue shows the active source and nothing
 *   else (CW-16).
 * - **`in_continue`**, the server's answer — or, for an episode only, **finished
 *   and not removed**: that one does not resume, but it can hand its card to the
 *   next episode (CW-19). A film that is finished has nothing to hand over.
 * - **A removed row never counts.** Removing an episode's card hides every
 *   episode of its series that has progress (contract, `hideProgress`), so a
 *   series with nothing left after this filter is a removed series.
 * - **Sorted here**, although each answer of the API is sorted already: films and
 *   episodes are asked for separately, and two sorted lists concatenated are not
 *   a sorted list. A timestamp that does not parse sorts last.
 */
export function continueRows<R extends ProgressRow>(rows: readonly R[], sourceId: string): R[] {
  const at = (row: R) => {
    const parsed = Date.parse(row.updated_at);
    return Number.isNaN(parsed) ? Number.NEGATIVE_INFINITY : parsed;
  };

  return rows
    .filter((row) => row.source_id === sourceId)
    .filter((row) => !row.hidden)
    .filter(
      (row) =>
        row.in_continue ||
        (row.item_type === "EPISODE" && isFinished(row.position_ms, row.duration_ms ?? null)),
    )
    .sort((a, b) => at(b) - at(a));
}

/** A row that survived step two, with what is known about it so far. */
export type ContinueCandidate<R extends ProgressRow, E extends EpisodeRef> =
  | { kind: "film"; row: R }
  /** An episode to resume, the server having put it in Continue. */
  | { kind: "episode"; row: R; episode: E }
  /** A finished episode: its series carries on with the next one, if any. */
  | { kind: "finished"; row: R; episode: E };

/**
 * Step two: **one card per series**, never one per episode.
 *
 * Rows arrive most recent first, so the first row met for a series decides its
 * card: the episode to resume, or a finished one whose next episode is proposed.
 * Every later row of that series is skipped.
 *
 * An episode the resolver did not return was dropped by the last
 * re-synchronisation: it falls out, and does **not** claim its series' slot,
 * since the series it belonged to is precisely what is no longer known.
 */
export function onePerSeries<R extends ProgressRow, E extends EpisodeRef>(
  rows: readonly R[],
  episodesById: ReadonlyMap<string, E>,
): ContinueCandidate<R, E>[] {
  const seen = new Set<string>();
  const out: ContinueCandidate<R, E>[] = [];

  for (const row of rows) {
    if (row.item_type === "VOD") {
      if (row.in_continue) out.push({ kind: "film", row });
      continue;
    }
    const episode = episodesById.get(row.item_ref);
    if (!episode || seen.has(episode.series_id)) continue;
    seen.add(episode.series_id);
    out.push({ kind: row.in_continue ? "episode" : "finished", row, episode });
  }
  return out;
}

/** The series whose tree step three needs: those whose latest episode is finished. */
export function seriesNeedingTree(
  candidates: readonly ContinueCandidate<ProgressRow, EpisodeRef>[],
): string[] {
  return [
    ...new Set(
      candidates.flatMap((candidate) =>
        candidate.kind === "finished" ? [candidate.episode.series_id] : [],
      ),
    ),
  ];
}

/** What step three hands to the cards: an episode to start, and from where. */
export type ContinueTarget<R extends ProgressRow, E extends EpisodeRef> =
  | { kind: "film"; row: R; positionMs: number; durationMs: number | null; hide: HideRef }
  | {
      kind: "episode";
      episode: E;
      positionMs: number;
      durationMs: number | null;
      hide: HideRef;
    };

/**
 * What "Remove from Continue" sends. For a series it is the episode that holds
 * the card's progress — the server hides the whole series from it — never the
 * proposed next episode, which may have no progress at all.
 */
export type HideRef = { itemType: ProgressItemType; itemRef: string };

/**
 * Step three: a finished episode becomes its series' next episode.
 *
 * The next episode is the one after it in the series' order (`nextAfter`):
 * started and unfinished, the card resumes it (CW-12); finished already, the
 * card starts it over rather than skipping ahead (CW-21); never started, it
 * starts at zero with no progress invented (CW-19). After the last episode the
 * series leaves the rail (CW-15), and a series whose tree could not be read
 * falls out of this rendering rather than proposing a guess.
 *
 * @param trees the series' episodes in watching order, by series id
 * @param episodeRows every saved episode row of the source, hidden or not —
 *   the next episode's own position lives there
 */
export function resolveTargets<R extends ProgressRow, E extends EpisodeRef>(
  candidates: readonly ContinueCandidate<R, E>[],
  trees: ReadonlyMap<string, readonly E[]>,
  episodeRows: readonly R[],
): ContinueTarget<R, E>[] {
  const out: ContinueTarget<R, E>[] = [];
  for (const candidate of candidates) {
    const hide: HideRef = { itemType: candidate.row.item_type, itemRef: candidate.row.item_ref };

    if (candidate.kind === "film") {
      out.push({
        kind: "film",
        row: candidate.row,
        positionMs: candidate.row.position_ms,
        durationMs: candidate.row.duration_ms ?? null,
        hide,
      });
      continue;
    }

    if (candidate.kind === "episode") {
      out.push({
        kind: "episode",
        episode: candidate.episode,
        positionMs: candidate.row.position_ms,
        durationMs: candidate.row.duration_ms ?? null,
        hide,
      });
      continue;
    }

    const tree = trees.get(candidate.episode.series_id);
    if (!tree) continue;
    const rows = episodeRows.map((row) => ({
      item_ref: row.item_ref,
      position_ms: row.position_ms,
      duration_ms: row.duration_ms,
      updated_at: row.updated_at,
    }));
    const next = nextAfter(
      candidate.episode.id,
      tree.map((episode) => episode.id),
      rows,
    );
    const episode = next ? tree.find((entry) => entry.id === next.episodeId) : undefined;
    if (!next || !episode) continue;

    out.push({
      kind: "episode",
      episode,
      positionMs: next.startMs,
      // A bar only where the episode has a duration of its own and a position to
      // show; a next episode never started draws none.
      durationMs: next.startMs > 0 ? (next.row?.duration_ms ?? null) : null,
      hide,
    });
  }
  return out;
}

/** What the rail draws. */
export type ContinueCard<E extends EpisodeRef> =
  | {
      kind: "film";
      key: string;
      film: VodItem;
      positionMs: number;
      durationMs: number | null;
      hide: HideRef;
    }
  | {
      kind: "episode";
      key: string;
      episode: E;
      series: Series;
      positionMs: number;
      durationMs: number | null;
      hide: HideRef;
    };

/**
 * Step four: titles and posters, then the cap.
 *
 * **The cap comes last.** A film or a series the last re-synchronisation dropped
 * is absent from the lookups and falls out here; cutting to twelve before that
 * would leave a rail of nine for no reason anybody could see.
 */
export function continueCards<R extends ProgressRow, E extends EpisodeRef>(
  targets: readonly ContinueTarget<R, E>[],
  filmsById: ReadonlyMap<string, VodItem>,
  seriesById: ReadonlyMap<string, Series>,
  limit: number,
): ContinueCard<E>[] {
  const out: ContinueCard<E>[] = [];

  for (const target of targets) {
    if (out.length === limit) break;
    const { positionMs, durationMs, hide } = target;

    if (target.kind === "film") {
      const film = filmsById.get(target.row.item_ref);
      if (film) {
        out.push({ kind: "film", key: `film:${film.id}`, film, positionMs, durationMs, hide });
      }
      continue;
    }

    const series = seriesById.get(target.episode.series_id);
    if (series) {
      out.push({
        kind: "episode",
        key: `series:${series.id}`,
        episode: target.episode,
        series,
        positionMs,
        durationMs,
        hide,
      });
    }
  }
  return out;
}
