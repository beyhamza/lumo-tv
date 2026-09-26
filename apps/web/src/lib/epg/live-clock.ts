"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { nextTransitionAt, type TimedProgramme } from "./programme";

/**
 * The client-side half of the sheet's clock (S9-06-04, GD-07/08).
 *
 * <h2>The problem this solves</h2>
 *
 * The panel must watch a programme end and lose its action **without a
 * reload**, so the decision cannot live only in the Server Component render.
 * But the pinned clock of a qualification session (`LUMO_NOW`) is read by
 * `lib/epg/clock.ts`, which is `server-only`: a client component can neither
 * import it nor read `process.env.LUMO_NOW` in the browser. Reading the
 * browser's own clock instead would make the sheet and the server disagree
 * whenever the session pins an instant.
 *
 * <h2>What it does instead</h2>
 *
 * The server hands over the instant it rendered with (`epgNow()`), and the hook
 * advances that instant by the browser's **elapsed** time since mount — never by
 * the wall clock. A sheet pinned to 20:59 therefore reaches 21:00 after one real
 * second, whatever the browser's date is. Elapsed time is read from
 * `performance.now()` (monotonic: a system-clock adjustment cannot jump it), so
 * the hook is correct across a midnight or a DST change for the same reason the
 * grid is (GD-12).
 *
 * <h2>It sleeps between transitions</h2>
 *
 * A timer is armed for the one instant {@link nextTransitionAt} names, and
 * nothing else runs: no polling, no interval. When it fires, the hook takes the
 * live instant and re-computes; a future programme that has just started arms
 * the timer again for its end, and a past one arms nothing.
 */

/** A short grace so "the end" is unambiguously past when the instant is read. */
export const CLOCK_TICK_GRACE_MS = 50;

export type ProgrammeClock = {
  /** The instant to draw with. Changes exactly when the moment changes. */
  now: number;
  /** The live instant right now — read again at activation (GD-08). */
  instant: () => number;
};

/**
 * @param programme the open programme, for its two instants.
 * @param serverNowMs the instant the server rendered this sheet with.
 */
export function useProgrammeClock(
  programme: TimedProgramme,
  serverNowMs: number,
): ProgrammeClock {
  const [now, setNow] = useState(serverNowMs);
  // `null` during server rendering and the first hydration pass: reading
  // `performance.now()` there would make the two renders disagree.
  const baseline = useRef<{ serverNowMs: number; startedAt: number } | null>(null);

  // The baseline is set in an effect, never during render: reading
  // `performance.now()` while rendering would make the server pass and the
  // hydration pass disagree. No `setNow` here — `now` starts at
  // `serverNowMs`, and this effect only anchors the elapsed-time origin.
  useEffect(() => {
    baseline.current = { serverNowMs, startedAt: performance.now() };
  }, [serverNowMs]);

  const instant = useCallback((): number => {
    const base = baseline.current;
    if (!base) return serverNowMs;
    return base.serverNowMs + (performance.now() - base.startedAt);
  }, [serverNowMs]);

  const boundary = nextTransitionAt(programme, now);

  useEffect(() => {
    if (boundary === undefined) return;
    const delay = Math.max(0, boundary - now) + CLOCK_TICK_GRACE_MS;
    const id = setTimeout(() => setNow(instant()), delay);
    return () => clearTimeout(id);
  }, [boundary, now, instant]);

  return { now, instant };
}
