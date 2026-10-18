/**
 * What happens when an episode really ends on the web (US-15 complement,
 * S13-03, CW-08/12/13/14/15, docs/design/0.2.0/resume-player.md).
 *
 * A small state machine, pure so that every rule is a test:
 *
 * - Only the **real end** (`ended` from the element) starts anything. Passing
 *   95 % changes the sheet, never the player (CW-08): there is no event for
 *   it here at all.
 * - With a next episode and autoplay on: a **5-second** countdown (mobile/web,
 *   CW-13). Any interaction with the page cancels it; "Play now" still works.
 *   A programmatic focus is not an interaction — the caller only feeds real
 *   input events.
 * - Autoplay off: the card without a countdown; only "Play next" launches
 *   (CW-14).
 * - No next episode (last one available): back to the series sheet, no empty
 *   countdown (CW-15).
 * - Playing again (a seek back, Replay) takes the card away.
 */
export const COUNTDOWN_SECONDS = 5;

export type EndState =
  | { phase: "playing" }
  | { phase: "counting"; remaining: number }
  | { phase: "offered"; cancelled: boolean }
  | { phase: "launch-next" }
  | { phase: "to-series" };

export type EndEvent =
  | { type: "ended"; hasNext: boolean; autoplay: boolean }
  | { type: "tick" }
  | { type: "interact" }
  | { type: "play-now" }
  | { type: "resumed" };

export const PLAYING: EndState = { phase: "playing" };

export function endReducer(state: EndState, event: EndEvent): EndState {
  switch (event.type) {
    case "ended":
      if (state.phase !== "playing") return state;
      if (!event.hasNext) return { phase: "to-series" };
      return event.autoplay
        ? { phase: "counting", remaining: COUNTDOWN_SECONDS }
        : { phase: "offered", cancelled: false };
    case "tick":
      if (state.phase !== "counting") return state;
      return state.remaining <= 1
        ? { phase: "launch-next" }
        : { phase: "counting", remaining: state.remaining - 1 };
    case "interact":
      return state.phase === "counting" ? { phase: "offered", cancelled: true } : state;
    case "play-now":
      return state.phase === "counting" || state.phase === "offered"
        ? { phase: "launch-next" }
        : state;
    case "resumed":
      // Once a navigation is decided it is not taken back by a stray event.
      return state.phase === "counting" || state.phase === "offered" ? PLAYING : state;
  }
}
