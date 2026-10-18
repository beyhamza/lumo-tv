import { describe, expect, it } from "vitest";
import { COUNTDOWN_SECONDS, type EndEvent, type EndState, PLAYING, endReducer } from "./next-episode";

function run(events: EndEvent[], from: EndState = PLAYING): EndState {
  return events.reduce(endReducer, from);
}

const ended = (hasNext: boolean, autoplay: boolean): EndEvent => ({ type: "ended", hasNext, autoplay });
const tick: EndEvent = { type: "tick" };

describe("the end of an episode on the web (S13-03)", () => {
  it("counts down 5 seconds, then launches the next (CW-13)", () => {
    expect(COUNTDOWN_SECONDS).toBe(5);
    expect(run([ended(true, true)])).toEqual({ phase: "counting", remaining: 5 });
    expect(run([ended(true, true), tick, tick, tick, tick])).toEqual({ phase: "counting", remaining: 1 });
    expect(run([ended(true, true), tick, tick, tick, tick, tick])).toEqual({ phase: "launch-next" });
  });

  it("is cancelled by any interaction, and Play now still works (CW-13)", () => {
    const cancelled = run([ended(true, true), tick, { type: "interact" }]);
    expect(cancelled).toEqual({ phase: "offered", cancelled: true });
    // The clock has stopped: no tick launches anything any more.
    expect(run([tick, tick, tick, tick, tick], cancelled)).toEqual(cancelled);
    expect(endReducer(cancelled, { type: "play-now" })).toEqual({ phase: "launch-next" });
  });

  it("offers without a countdown when autoplay is off (CW-14)", () => {
    const offered = run([ended(true, false), tick, tick, tick, tick, tick, tick]);
    expect(offered).toEqual({ phase: "offered", cancelled: false });
    expect(endReducer(offered, { type: "play-now" })).toEqual({ phase: "launch-next" });
  });

  it("returns to the series after the last episode, with no countdown (CW-15)", () => {
    expect(run([ended(false, true)])).toEqual({ phase: "to-series" });
    expect(run([ended(false, false)])).toEqual({ phase: "to-series" });
  });

  it("takes the card away when playback starts again", () => {
    expect(run([ended(true, true), { type: "resumed" }])).toEqual(PLAYING);
    expect(run([ended(true, false), { type: "resumed" }])).toEqual(PLAYING);
  });

  it("does nothing before the real end: no tick, no interaction, no Play now (CW-08)", () => {
    // Passing 95 % is not an event of this machine at all; nothing else starts it.
    expect(run([tick, { type: "interact" }, { type: "play-now" }, { type: "resumed" }])).toEqual(PLAYING);
  });

  it("does not take back a decided navigation", () => {
    expect(endReducer({ phase: "launch-next" }, { type: "resumed" })).toEqual({ phase: "launch-next" });
    expect(endReducer({ phase: "to-series" }, ended(true, true))).toEqual({ phase: "to-series" });
  });
});
