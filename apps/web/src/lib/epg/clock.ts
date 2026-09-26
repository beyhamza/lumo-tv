import "server-only";

/**
 * The server-side EPG clock (I-3, S9-07-03).
 *
 * <h2>One clock, two callers</h2>
 *
 * The Guide (`channels/page.tsx`) and the home rails (`load-home-rails.ts`) both
 * call {@link loadEpgWindow}, and both decide "what is on now" from an instant.
 * Patch one and the other keeps the real clock, so the Guide and "En ce moment"
 * can disagree about which programme is on. `LUMO_NOW` is therefore read in this
 * one place, and both callers take their instant from {@link epgNow}.
 *
 * <h2>Why an environment override at all</h2>
 *
 * A qualification session has to move the clock — a programme that ends must be
 * watched ending, and a future one watched starting (GD-07/08) — without waiting
 * for real time. Playwright's `page.clock` reaches the browser and cannot reach
 * Server Component rendering, so the session pins the instant here instead:
 *
 *   LUMO_NOW=2026-09-26T20:00:00Z
 *
 * <h2>Only when it parses</h2>
 *
 * A value that is absent means the real clock, which is what ships. A value that
 * is present but unreadable is a mistake (a typo, an empty variable some
 * deployment interpolated), and moving the whole product to 1970 for it would be
 * worse than ignoring it: it falls back to the real clock, never throws. The
 * recette's own guard is that a pinned session announces its instant, which it
 * does in the bench log (`BENCH_EPG_ANCHOR`, I-1).
 */
export function epgNow(): Date {
  const pinned = process.env.LUMO_NOW;
  if (pinned) {
    const at = Date.parse(pinned);
    if (!Number.isNaN(at)) {
      return new Date(at);
    }
  }
  return new Date();
}
