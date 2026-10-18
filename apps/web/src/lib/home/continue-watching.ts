import type { Episode, PlaybackProgress, ProgressItemType, Series, VodItem } from "@/lib/api/types";
import { type LaunchKind, launchQuery } from "@/lib/playback/continue-launch";
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
 *
 * <h2>Gone is not failed (CW-28, S12-05)</h2>
 *
 * A row whose film or episode a lookup did not return is one of two things, and
 * only the loader knows which. The lookup **answered** and the item is not in
 * it: the last re-synchronisation removed it, and the card says so —
 * "Unavailable", with "Remove from Continue" as its only action, the progress
 * untouched. The lookup **failed** — or was never asked, past a cap — and
 * nothing is known: the row falls out of this rendering and the rail reports
 * itself incomplete (S11-05). An outage never reads as a removal.
 *
 * So each step that resolves ids takes, beside what was found, the ids a
 * *successful* lookup was asked about (`confirmed`). Absent from the answer and
 * in that set: gone. Anything else absent: unknown.
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
  /** In Continue, and absent from an episode lookup that answered (CW-28). */
  | { kind: "gone"; row: R }
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
 * An episode the resolver did not return does **not** claim its series' slot,
 * since the series it belonged to is precisely what is no longer known. Asked
 * of a lookup that answered (`confirmedEpisodes`), it was removed by the last
 * re-synchronisation: in Continue, it becomes a `gone` card of its own (CW-28);
 * finished, it had nothing to resume and no tree to hand over to, and falls
 * out. Otherwise nothing is known about it and it falls out too.
 *
 * @param confirmedEpisodes the ids a successful episode lookup was asked
 *   about; empty when that lookup failed
 */
export function onePerSeries<R extends ProgressRow, E extends EpisodeRef>(
  rows: readonly R[],
  episodesById: ReadonlyMap<string, E>,
  confirmedEpisodes: ReadonlySet<string> = new Set(),
): ContinueCandidate<R, E>[] {
  const seen = new Set<string>();
  const out: ContinueCandidate<R, E>[] = [];

  for (const row of rows) {
    if (row.item_type === "VOD") {
      if (row.in_continue) out.push({ kind: "film", row });
      continue;
    }
    const episode = episodesById.get(row.item_ref);
    if (!episode) {
      if (row.in_continue && confirmedEpisodes.has(row.item_ref)) out.push({ kind: "gone", row });
      continue;
    }
    if (seen.has(episode.series_id)) continue;
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
      /**
       * How the card launches it (S12-05): `resume` the episode the card was
       * saved on, or `next` — the series' next episode after a finished one,
       * whose failure to start sends back to the series (CW-27).
       */
      launch: LaunchKind;
      positionMs: number;
      durationMs: number | null;
      hide: HideRef;
    }
  | { kind: "gone"; row: R; positionMs: number; durationMs: number | null; hide: HideRef };

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

    if (candidate.kind === "gone") {
      out.push({
        kind: "gone",
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
        launch: "resume",
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
      launch: "next",
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
      launch: LaunchKind;
      positionMs: number;
      durationMs: number | null;
      hide: HideRef;
    }
  /**
   * Unavailable (CW-28): its item is no longer in the catalogue. No title — a
   * progress row carries none — but the episode's numbers when they are known
   * (an episode found, its series gone). Its only action is "Remove from
   * Continue"; its progress is never deleted.
   */
  | {
      kind: "gone";
      key: string;
      episode?: E;
      positionMs: number;
      durationMs: number | null;
      hide: HideRef;
    };

/**
 * Step four: titles and posters, then the cap.
 *
 * A film or a series absent from a lookup that answered for it was dropped by
 * the last re-synchronisation: its card says "Unavailable" (CW-28). Absent from
 * a lookup that failed, or that was not asked about it, it falls out of this
 * rendering — the rail then reports itself incomplete (S11-05).
 *
 * **The cap comes last**, so a row that falls out does not cost a card: cutting
 * to twelve before would leave a rail of nine for no reason anybody could see.
 *
 * @param confirmed the ids a successful film and series lookup was asked about
 */
export function continueCards<R extends ProgressRow, E extends EpisodeRef>(
  targets: readonly ContinueTarget<R, E>[],
  filmsById: ReadonlyMap<string, VodItem>,
  seriesById: ReadonlyMap<string, Series>,
  limit: number,
  confirmed: { films?: ReadonlySet<string>; series?: ReadonlySet<string> } = {},
): ContinueCard<E>[] {
  const out: ContinueCard<E>[] = [];
  const gone = (target: ContinueTarget<R, E>, episode?: E): ContinueCard<E> => ({
    kind: "gone",
    key: `gone:${target.hide.itemType}:${target.hide.itemRef}`,
    ...(episode ? { episode } : {}),
    positionMs: target.positionMs,
    durationMs: target.durationMs,
    hide: target.hide,
  });

  for (const target of targets) {
    if (out.length === limit) break;
    const { positionMs, durationMs, hide } = target;

    if (target.kind === "gone") {
      out.push(gone(target));
      continue;
    }

    if (target.kind === "film") {
      const film = filmsById.get(target.row.item_ref);
      if (film) {
        out.push({ kind: "film", key: `film:${film.id}`, film, positionMs, durationMs, hide });
      } else if (confirmed.films?.has(target.row.item_ref)) {
        out.push(gone(target));
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
        launch: target.launch,
        positionMs,
        durationMs,
        hide,
      });
    } else if (confirmed.series?.has(target.episode.series_id)) {
      out.push(gone(target, target.episode));
    }
  }
  return out;
}

/**
 * Where a card's two links go (US-019, S12-05), as paths without the locale.
 *
 * - **Main — play now.** The sheet that holds the player, with `launch` and
 *   `at` (`lib/playback/continue-launch.ts`): the player starts at the card's
 *   position, no "Resume" button to press. A film's sheet is its own page; an
 *   episode's is its series' page with `season` and `play`.
 * - **Secondary — the details.** The same sheet with nothing playing.
 *
 * A gone card has neither (CW-28): there is nothing left to open.
 */
export function cardLinks(
  card: ContinueCard<EpisodeRef>,
  sourceId: string,
): { play: string; details: string } | null {
  if (card.kind === "gone") return null;

  if (card.kind === "film") {
    const details = `/app/sources/${sourceId}/vod/${card.film.id}`;
    const query = new URLSearchParams(launchQuery("resume", card.positionMs));
    return { play: `${details}?${query}`, details };
  }

  const details = `/app/sources/${sourceId}/series/${card.series.id}`;
  const query = new URLSearchParams({
    season: String(card.episode.season_number),
    play: card.episode.id,
    ...launchQuery(card.launch, card.positionMs),
  });
  return { play: `${details}?${query}`, details };
}
