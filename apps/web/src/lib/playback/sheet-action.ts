import { isFinished } from "./progress";

/**
 * What a sheet offers to start playback with (S12-03, US-019 "Parcours depuis
 * les fiches").
 *
 * - **Watch** — nothing saved, or a position of zero.
 * - **Resume at mm:ss**, with **Start over** beside it — a position short of
 *   the end, or an unknown duration (CW-09: no percentage is invented).
 * - **Watch again** — strictly past 95 % of a known duration (CW-07); it starts
 *   from the beginning, so a second "from the start" button would be the same
 *   button twice.
 *
 * Whether the item is hidden from Continue changes nothing here: removing a card
 * keeps its position, and the sheet is where it is found again (CW-10).
 */
export type SheetChoice =
  | { kind: "watch" }
  | { kind: "resume"; atMs: number }
  | { kind: "rewatch" };

type Saved = { position_ms: number; duration_ms?: number | null } | null | undefined;

export function sheetChoice(saved: Saved): SheetChoice {
  if (!saved || !(saved.position_ms > 0)) return { kind: "watch" };
  if (isFinished(saved.position_ms, saved.duration_ms ?? null)) return { kind: "rewatch" };
  return { kind: "resume", atMs: saved.position_ms };
}

/** One button of the player: its words, and where it starts. */
export type PlayerAction = { label: string; fromMs: number };
export type PlayerActions = { primary: PlayerAction; secondary: PlayerAction | null };

/** The buttons for a choice, in the page's language. */
export function playerActions(
  choice: SheetChoice,
  words: {
    watch: string;
    resumeAt: (atMs: number) => string;
    rewatch: string;
    restart: string;
  },
): PlayerActions {
  switch (choice.kind) {
    case "watch":
      return { primary: { label: words.watch, fromMs: 0 }, secondary: null };
    case "rewatch":
      return { primary: { label: words.rewatch, fromMs: 0 }, secondary: null };
    case "resume":
      return {
        primary: { label: words.resumeAt(choice.atMs), fromMs: choice.atMs },
        secondary: { label: words.restart, fromMs: 0 },
      };
  }
}

/** A saved episode position, as much of `PlaybackProgress` as this needs. */
export type EpisodeRow = {
  item_ref: string;
  position_ms: number;
  duration_ms?: number | null;
  updated_at: string;
};

/**
 * Where a series carries on, given its saved episodes and its episodes in order.
 *
 * - The most recent episode, unfinished: **resume** it (whatever its watched
 *   time — the sheet resumes what Continue would not yet show, CW-01).
 * - The most recent episode, finished: the **next** one in the series' order
 *   (CW-08, CW-19). Started and unfinished, it resumes at its position (CW-12);
 *   finished already, it starts over — never skipping ahead to an unfinished one
 *   (CW-21); never started, it starts at zero with no progress invented (CW-19).
 * - Nothing after it: **null** — the series is done (CW-15).
 * - Nothing saved: **null** as well; the caller offers the first episode.
 */
export type Continuation = {
  episodeId: string;
  kind: "resume" | "next";
  startMs: number;
  /** The saved row of the target episode, when there is one. */
  row: EpisodeRow | null;
};

export function seriesContinuation(
  rows: readonly EpisodeRow[],
  orderedEpisodeIds: readonly string[],
): Continuation | null {
  const inSeries = new Set(orderedEpisodeIds);
  const mine = rows.filter((row) => inSeries.has(row.item_ref));
  const latest = mostRecent(mine);
  if (!latest) return null;

  if (!isFinished(latest.position_ms, latest.duration_ms ?? null)) {
    return { episodeId: latest.item_ref, kind: "resume", startMs: latest.position_ms, row: latest };
  }
  return nextAfter(latest.item_ref, orderedEpisodeIds, mine);
}

/** The episode after `finishedId`, and where to start it — or null at the end. */
export function nextAfter(
  finishedId: string,
  orderedEpisodeIds: readonly string[],
  rows: readonly EpisodeRow[],
): Continuation | null {
  const at = orderedEpisodeIds.indexOf(finishedId);
  if (at < 0 || at === orderedEpisodeIds.length - 1) return null;

  const nextId = orderedEpisodeIds[at + 1];
  const row = rows.find((candidate) => candidate.item_ref === nextId) ?? null;
  const resumable =
    row !== null && row.position_ms > 0 && !isFinished(row.position_ms, row.duration_ms ?? null);
  return { episodeId: nextId, kind: "next", startMs: resumable ? row.position_ms : 0, row };
}

/**
 * The series' episodes in watching order: seasons by number, episodes by number,
 * the panel's own order breaking ties. Season 0 — specials — sorts first, as the
 * sheet lists it.
 */
export function orderedEpisodeIds(
  seasons: readonly {
    season_number: number;
    episodes: readonly { id: string; episode_number: number }[];
  }[],
): string[] {
  return [...seasons]
    .sort((a, b) => a.season_number - b.season_number)
    .flatMap((season) =>
      season.episodes
        .map((episode, index) => ({ episode, index }))
        .sort((a, b) => a.episode.episode_number - b.episode.episode_number || a.index - b.index)
        .map(({ episode }) => episode.id),
    );
}

function mostRecent<R extends { updated_at: string }>(rows: readonly R[]): R | undefined {
  const at = (row: R) => {
    const parsed = Date.parse(row.updated_at);
    return Number.isNaN(parsed) ? Number.NEGATIVE_INFINITY : parsed;
  };
  let best: R | undefined;
  for (const row of rows) if (!best || at(row) > at(best)) best = row;
  return best;
}
