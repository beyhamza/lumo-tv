/**
 * The two rules a saved position obeys, in one place (S5-11).
 *
 * They are pure functions rather than checks buried in a component because both
 * are the kind of thing that is only ever wrong in production, and because the
 * sprint asks each client to hold one of them with a test.
 */

/**
 * Whether a position is worth sending to the server.
 *
 * <p><b>A live stream is never saved.</b> `ProgressItemType` has no `LIVE` value,
 * so the contract cannot express it — but a type system does not stop a player
 * component being reused for a channel and writing what it reports. That is
 * exactly the accident the sprint names, and this is the guard for it.
 *
 * <p><b>A position of zero is not saved either</b>, and that is not thrift: a
 * save at zero overwrites a real position with the beginning of the film. A
 * `<video>` reports zero for every moment before the first frame decodes, so
 * without this, opening a film and closing the tab immediately would lose where
 * somebody was — the exact case resume exists for.
 */
export function savableProgress(input: {
  positionMs: number;
  isLive: boolean;
}): boolean {
  if (input.isLive) return false;
  return Number.isFinite(input.positionMs) && input.positionMs > 0;
}

/**
 * Whether a film counts as watched, and therefore leaves the rail.
 *
 * <p><b>A threshold, not an event.</b> Nothing tells us a film ended: a tab
 * closed, a laptop shut and somebody who watched the credits all stop writing at
 * some position. So "finished" is the position being close enough to the end
 * that offering to resume would be absurd — 95 %, because the last few minutes
 * are credits often enough that a stricter line puts finished films back in the
 * rail.
 *
 * <p><b>With no duration it is never finished, and that default is the whole
 * decision.</b> Many panels state no running time at all. A film that lingers in
 * the rail is an annoyance somebody dismisses; a film that vanishes from it
 * before the end is a loss they cannot recover, because nothing else records
 * where they were.
 */
export function isFinished(positionMs: number, durationMs: number | null): boolean {
  if (durationMs == null || durationMs <= 0) return false;
  return positionMs >= durationMs * FINISHED_FRACTION;
}

const FINISHED_FRACTION = 0.95;

/** Milliseconds as a clock, the hour only when there is one. */
export function asClock(milliseconds: number): string {
  const totalSeconds = Math.max(0, Math.floor(milliseconds / 1000));
  const seconds = totalSeconds % 60;
  const minutes = Math.floor(totalSeconds / 60) % 60;
  const hours = Math.floor(totalSeconds / 3600);
  const pad = (value: number) => String(value).padStart(2, "0");

  return hours > 0 ? `${hours}:${pad(minutes)}:${pad(seconds)}` : `${minutes}:${pad(seconds)}`;
}
