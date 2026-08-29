import { describe, expect, it } from "vitest";
import { asClock, isFinished, savableProgress } from "./progress";

/**
 * The two rules a saved position obeys (S5-11).
 *
 * <h2>Why the live guard is tested here, on the web, and not only on Android</h2>
 *
 * The sprint asks for it on **each** client, and the reason it gives is precise:
 * *"c'est le genre d'appel qu'un lecteur partagé entre deux usages fait tout
 * seul."* `ProgressItemType` has no `LIVE` value, so a channel's position cannot
 * be expressed — that much the type system already prevents. What it does not
 * prevent is a player component being reused for a channel and calling this with
 * whatever the element reports.
 *
 * <h2>And why the finished threshold is worth its own test</h2>
 *
 * Because the interesting case is the *absence* of a duration, which is most
 * films on most panels. Getting it the other way round empties the
 * "continue watching" rail on exactly the films it exists for, and nothing
 * crashes when it does.
 */
describe("savableProgress", () => {
  it("never saves a live stream, whatever its position says", () => {
    expect(savableProgress({ positionMs: 90_000, isLive: true })).toBe(false);
  });

  it("never saves position zero", () => {
    // Not thrift: a save at zero overwrites a real position with the beginning
    // of the film, and a `<video>` reports zero until the first frame decodes.
    expect(savableProgress({ positionMs: 0, isLive: false })).toBe(false);
  });

  it("saves a film that is playing", () => {
    expect(savableProgress({ positionMs: 1_214_000, isLive: false })).toBe(true);
  });

  it("refuses a position that is not a number", () => {
    // `video.currentTime` is NaN before metadata loads on some browsers, and
    // `NaN > 0` is false — but so is `NaN < 0`, so this is asserted rather than
    // assumed.
    expect(savableProgress({ positionMs: Number.NaN, isLive: false })).toBe(false);
  });
});

describe("isFinished", () => {
  it("is never finished without a duration", () => {
    // The case that matters: many panels state no running time at all. A film
    // that lingers in the rail is an annoyance; one that vanishes before the end
    // is a loss nothing else can recover.
    expect(isFinished(7_200_000, null)).toBe(false);
  });

  it("treats a duration of zero as no duration", () => {
    // Some sources send `0`. Dividing by it would make every position finished.
    expect(isFinished(1_000, 0)).toBe(false);
  });

  it("is finished past ninety-five per cent", () => {
    expect(isFinished(5_184_000, 5_400_000)).toBe(true);
  });

  it("is not finished just under it", () => {
    expect(isFinished(5_076_000, 5_400_000)).toBe(false);
  });
});

describe("asClock", () => {
  it("drops the hour when there is none", () => {
    expect(asClock(1_214_000)).toBe("20:14");
  });

  it("keeps it when there is one", () => {
    expect(asClock(6_423_000)).toBe("1:47:03");
  });

  it("never renders a negative position", () => {
    expect(asClock(-5_000)).toBe("0:00");
  });
});
