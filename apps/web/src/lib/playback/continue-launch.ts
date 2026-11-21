/**
 * Starting playback straight from a "Continue" card (US-019, S12-05).
 *
 * <h2>The card plays; the sheet offers</h2>
 *
 * A sheet opened by hand offers "Resume at…" beside "Start over" and presses
 * neither (S5-11, S12-03). A Continue card is the other door: its main action
 * *is* resuming, with no question in between (US-019: "L'accueil Continuer
 * conserve sa reprise immédiate"). So the card's link carries two facts in its
 * URL, and the sheet starts its player with them:
 *
 * - `launch` — `resume` for the item the card was saved on, `next` for the
 *   series' next episode proposed after a finished one (CW-19, CW-21). The
 *   difference matters only when it fails (CW-27).
 * - `at` — where to start, in milliseconds, as the rail decided it
 *   (`resolveTargets`): the saved position, or zero for a next episode never
 *   started or already finished.
 *
 * Both are read back through `readLaunch`, which accepts nothing else: a URL
 * typed by hand starts at a sane place or does not autostart at all.
 *
 * <h2>When it fails</h2>
 *
 * A failed launch is not a start (continue-watching-cases, "Une tentative de
 * lancement échouée n'est pas un démarrage réel"): nothing is saved — no frame
 * played, no position past zero to write — so the card and its progress stay
 * exactly as they were (CW-25). What the player offers is decided by
 * `launchFailureActions`.
 */

export const LAUNCH_PARAM = "launch";
export const AT_PARAM = "at";

export type LaunchKind = "resume" | "next";

export type Launch = { kind: LaunchKind; atMs: number };

/**
 * The longest position accepted from a URL: a week. Far beyond any film, and
 * short enough that a mangled value does not turn into a seek to the year 3000.
 */
const MAX_AT_MS = 7 * 24 * 3600 * 1000;

/** The query a card adds to its sheet's URL. */
export function launchQuery(kind: LaunchKind, atMs: number): Record<string, string> {
  const at = Number.isFinite(atMs) && atMs > 0 ? Math.min(Math.floor(atMs), MAX_AT_MS) : 0;
  return { [LAUNCH_PARAM]: kind, [AT_PARAM]: String(at) };
}

/**
 * The launch a sheet was opened with, or null for a sheet opened by hand.
 *
 * `launch` must be one of the two kinds. `at` must be a plain non-negative
 * integer; absent, it is zero. Anything else — a negative, a fraction, `1e9`,
 * a value past a week — is not a launch, and the sheet behaves as if opened
 * by hand: offering is always safe, starting somewhere odd is not.
 */
export function readLaunch(query: {
  launch?: string | string[];
  at?: string | string[];
}): Launch | null {
  const kind = single(query.launch);
  if (kind !== "resume" && kind !== "next") return null;

  const raw = single(query.at);
  if (raw === undefined || raw === "") return { kind, atMs: 0 };
  if (!/^\d{1,12}$/.test(raw)) return null;
  const atMs = Number(raw);
  if (atMs > MAX_AT_MS) return null;
  return { kind, atMs };
}

/**
 * A sheet's query without a card's launch: for the links and forms that come
 * back to the sheet (the "My list" toggle, the series' own links). Coming back
 * must not start playback again, from a position that is no longer the latest.
 */
export function withoutLaunch<Q extends Record<string, string | string[] | undefined>>(
  query: Q,
): Q {
  const rest = { ...query };
  delete rest[LAUNCH_PARAM];
  delete rest[AT_PARAM];
  return rest;
}

/** Why a launched player stopped before playing. Mirrors the player's own failures. */
export type LaunchFailure =
  | { kind: "api"; code: string }
  | { kind: "mixed-content" }
  | { kind: "blocked" }
  | { kind: "unplayable" };

/**
 * The way out offered under a failed launch (CW-25, CW-27).
 *
 * - **Back.** Home for a resume — the card is still there, its progress
 *   untouched (CW-25). The series for a next episode: no other episode is tried
 *   in its place, the series is where somebody chooses (CW-27).
 * - **Retry**, only when trying again can change something. Mixed content is a
 *   browser rule, and a `*_NOT_FOUND` says the item is gone: offering to retry
 *   either would be asking somebody to do the same thing twice.
 */
export function launchFailureActions(
  kind: LaunchKind,
  failure: LaunchFailure,
): { retry: boolean; back: "home" | "series" } {
  return {
    retry: retryable(failure),
    back: kind === "next" ? "series" : "home",
  };
}

function retryable(failure: LaunchFailure): boolean {
  if (failure.kind === "mixed-content") return false;
  if (failure.kind === "api") return !failure.code.endsWith("_NOT_FOUND");
  return true;
}

/**
 * Whether a `play()` rejection is the browser refusing to start without a
 * gesture — autoplay with sound — rather than a playback failure.
 *
 * Only `NotAllowedError` is: the file is fine, the player is at the position,
 * and one press starts it. An `AbortError` is a load interrupted by another
 * (a retry, a new `src`) and says nothing either way; a real decoding failure
 * reaches the element's `error` event, which is where failures are named.
 */
export function autoplayRefused(error: unknown): boolean {
  return (
    typeof error === "object" &&
    error !== null &&
    (error as { name?: unknown }).name === "NotAllowedError"
  );
}

function single(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}
