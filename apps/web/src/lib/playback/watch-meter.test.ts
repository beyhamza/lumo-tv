import { describe, expect, it } from "vitest";
import {
  MAX_SEGMENTS_PER_SAVE,
  WatchMeter,
  isActuallyPlaying,
  sanitizeWatchExtras,
} from "./watch-meter";

const T0 = Date.parse("2026-10-15T20:00:00.000Z");
const at = (seconds: number) => T0 + seconds * 1000;
const iso = (seconds: number) => new Date(at(seconds)).toISOString();
const seconds = (segments: { started_at: string; ended_at: string }[]) =>
  segments.reduce(
    (sum, segment) => sum + (Date.parse(segment.ended_at) - Date.parse(segment.started_at)) / 1000,
    0,
  );

describe("WatchMeter", () => {
  it("records nothing before playback really starts", () => {
    const meter = new WatchMeter();
    expect(meter.take(at(10))).toEqual([]);
    expect(meter.sessionStartedAt).toBeNull();
  });

  it("counts play until pause, and not the pause (CW-05)", () => {
    const meter = new WatchMeter();
    meter.start(at(0));
    meter.stop(at(10)); // pause
    // Ten paused seconds, then play again.
    meter.start(at(20));
    meter.stop(at(25));

    expect(meter.take(at(60))).toEqual([
      { started_at: iso(0), ended_at: iso(10) },
      { started_at: iso(20), ended_at: iso(25) },
    ]);
  });

  it("does not count buffering (waiting closes, playing reopens)", () => {
    const meter = new WatchMeter();
    meter.start(at(0));
    meter.stop(at(8)); // waiting
    meter.start(at(14)); // playing again
    meter.stop(at(20));

    expect(seconds(meter.take(at(30)))).toBe(14);
  });

  it("gives a seek no watched time (CW-04)", () => {
    // 5 s watched, then a jump of 20 minutes: seeking closes, seeked reopens.
    const meter = new WatchMeter();
    meter.start(at(0));
    meter.stop(at(5)); // seeking
    meter.start(at(6)); // seeked, playing again at 20:00 in the media
    meter.stop(at(8)); // ended / pause

    expect(seconds(meter.take(at(10)))).toBe(7);
  });

  it("closes on ended", () => {
    const meter = new WatchMeter();
    meter.start(at(0));
    meter.stop(at(29)); // ended — a 29 s clip stays under the threshold (CW-20)
    expect(meter.playing).toBe(false);
    expect(seconds(meter.take(at(100)))).toBe(29);
  });

  it("ignores repeated start and stop events", () => {
    const meter = new WatchMeter();
    meter.start(at(0));
    meter.start(at(3)); // `playing` then `seeked` without a stop in between
    meter.stop(at(10));
    meter.stop(at(12));
    expect(meter.take(at(20))).toEqual([{ started_at: iso(0), ended_at: iso(10) }]);
  });

  it("cuts the open interval at each save and keeps it running", () => {
    const meter = new WatchMeter();
    meter.start(at(0));

    expect(meter.take(at(30))).toEqual([{ started_at: iso(0), ended_at: iso(30) }]);
    expect(meter.playing).toBe(true);
    expect(meter.take(at(60))).toEqual([{ started_at: iso(30), ended_at: iso(60) }]);
    // Nothing is sent twice.
    expect(meter.take(at(60))).toEqual([]);
  });

  it("keeps the session start at the first real playback", () => {
    const meter = new WatchMeter();
    meter.start(at(4));
    meter.stop(at(10));
    meter.start(at(50));
    expect(meter.sessionStartedAt).toBe(iso(4));
  });

  it(`sends at most ${MAX_SEGMENTS_PER_SAVE} intervals per save and keeps the rest`, () => {
    const meter = new WatchMeter();
    for (let i = 0; i < 25; i += 1) {
      meter.start(at(i * 2));
      meter.stop(at(i * 2 + 1));
    }

    const first = meter.take(at(100));
    expect(first).toHaveLength(MAX_SEGMENTS_PER_SAVE);
    expect(first[0].started_at).toBe(iso(0));
    expect(meter.waiting).toBe(5);

    const second = meter.take(at(130));
    expect(second).toHaveLength(5);
    expect(second[0].started_at).toBe(iso(40));
  });

  it("puts back what a failed save took, ahead of newer intervals", () => {
    const meter = new WatchMeter();
    meter.start(at(0));
    const lost = meter.take(at(30));
    meter.giveBack(lost);

    expect(meter.take(at(40))).toEqual([
      { started_at: iso(0), ended_at: iso(30) },
      { started_at: iso(30), ended_at: iso(40) },
    ]);
  });

  it("drops zero-length and backwards intervals", () => {
    const meter = new WatchMeter();
    meter.start(at(10));
    meter.stop(at(10));
    meter.start(at(20));
    meter.stop(at(15));
    expect(meter.take(at(30))).toEqual([]);
  });
});

describe("isActuallyPlaying", () => {
  const playing = { paused: false, seeking: false, ended: false, readyState: 4, visible: true };

  it("is true only when every condition holds", () => {
    expect(isActuallyPlaying(playing)).toBe(true);
    expect(isActuallyPlaying({ ...playing, paused: true })).toBe(false);
    expect(isActuallyPlaying({ ...playing, seeking: true })).toBe(false);
    expect(isActuallyPlaying({ ...playing, ended: true })).toBe(false);
    // HAVE_CURRENT_DATA: a frozen frame while buffering.
    expect(isActuallyPlaying({ ...playing, readyState: 2 })).toBe(false);
    expect(isActuallyPlaying({ ...playing, visible: false })).toBe(false);
  });
});

describe("sanitizeWatchExtras", () => {
  it("keeps well-formed values, normalised", () => {
    expect(
      sanitizeWatchExtras({
        watchedSegments: [{ started_at: iso(0), ended_at: iso(10) }],
        observedAt: iso(10),
        sessionStartedAt: "2026-10-15T22:00:00+02:00",
      }),
    ).toEqual({
      watched_segments: [{ started_at: iso(0), ended_at: iso(10) }],
      observed_at: iso(10),
      session_started_at: iso(0),
    });
  });

  it("drops malformed intervals and caps the list", () => {
    const many = Array.from({ length: 30 }, (_, i) => ({
      started_at: iso(i),
      ended_at: iso(i + 1),
    }));
    const result = sanitizeWatchExtras({
      watchedSegments: [
        { started_at: iso(5), ended_at: iso(1) },
        { started_at: "yesterday", ended_at: iso(1) },
        null,
        "x",
        ...many,
      ],
    });
    expect(result.watched_segments).toHaveLength(MAX_SEGMENTS_PER_SAVE);
    expect(result.watched_segments?.[0].started_at).toBe(iso(0));
  });

  it("omits what is absent or not a date", () => {
    expect(
      sanitizeWatchExtras({ watchedSegments: "nope", observedAt: 12, sessionStartedAt: "soon" }),
    ).toEqual({});
  });
});
