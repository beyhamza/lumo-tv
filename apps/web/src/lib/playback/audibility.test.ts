import { describe, expect, it } from "vitest";
import { advanceMs, audibility, SILENCE_AFTER_MS } from "./audibility";

/**
 * The rule that decides whether the page says "this browser has no sound for
 * this".
 *
 * <h2>What these tests are really guarding</h2>
 *
 * Not a calculation — there is barely one. They guard the **cost of being
 * wrong**, which is lopsided. A missed silence leaves the page exactly as it was
 * before this existed: no worse than the bug that prompted it. A false silence
 * tells somebody whose sound works fine that their browser cannot play it, and
 * sends them to install an application over nothing.
 *
 * So every case below that could go either way is asserted to go quiet.
 */
describe("audibility", () => {
  it("says nothing when the element is muted by the person watching", () => {
    // The one that would be genuinely insulting to get wrong. A missing track
    // does not set `muted` — the element stays unmuted and only the browser's
    // control renders as muted — so a `true` here is somebody's own finger.
    expect(
      audibility({ audioBytesDecoded: 0, playedMs: 60_000, muted: true }),
    ).toBe("unknown");
  });

  it("says nothing on a browser that offers no reading at all", () => {
    // No `audioTracks`, no `mozHasAudio`, no byte count. It may well be playing
    // sound; we have no way to ask, and guessing is worse than silence.
    expect(audibility({ playedMs: 600_000, muted: false })).toBe("unknown");
  });

  describe("where the browser declares its tracks", () => {
    it("is silent with none, before anything has played", () => {
      // Safari and Firefox know this from metadata, so the answer must not wait
      // on the grace the byte count needs.
      expect(audibility({ audioTrackCount: 0, playedMs: 0, muted: false })).toBe("silent");
    });

    it("is audible with one", () => {
      expect(audibility({ audioTrackCount: 1, playedMs: 0, muted: false })).toBe("audible");
    });

    it("reads `mozHasAudio` when that is what there is", () => {
      expect(audibility({ hasAudio: false, playedMs: 0, muted: false })).toBe("silent");
      expect(audibility({ hasAudio: true, playedMs: 0, muted: false })).toBe("audible");
    });

    it("prefers a declaration over a byte count that has not run yet", () => {
      // A browser with both. `audioTracks` is true the moment metadata is in;
      // zero decoded bytes at that instant means only that decoding has not
      // started, and letting it win would call every stream silent for three
      // seconds.
      expect(
        audibility({ audioTrackCount: 1, audioBytesDecoded: 0, playedMs: 0, muted: false }),
      ).toBe("audible");
    });
  });

  describe("where the browser only counts decoded bytes", () => {
    // Chromium, which is where the problem actually is: Chrome does not
    // implement `audioTracks`, and Chrome is the browser with no AC-3 decoder.

    it("is audible as soon as one byte is decoded", () => {
      expect(audibility({ audioBytesDecoded: 1, playedMs: 0, muted: false })).toBe("audible");
    });

    it("says nothing while nothing has played", () => {
      // Zero bytes at zero played is a file that is opening, not a file with no
      // sound — and on a slow panel that state lasts.
      expect(audibility({ audioBytesDecoded: 0, playedMs: 0, muted: false })).toBe("unknown");
    });

    it("still says nothing just under the grace", () => {
      expect(
        audibility({ audioBytesDecoded: 0, playedMs: SILENCE_AFTER_MS - 1, muted: false }),
      ).toBe("unknown");
    });

    it("is silent once media has played and nothing was decoded", () => {
      expect(
        audibility({ audioBytesDecoded: 0, playedMs: SILENCE_AFTER_MS, muted: false }),
      ).toBe("silent");
    });
  });
});

/**
 * How played time is counted, which is the whole reason the grace above means
 * anything.
 */
describe("advanceMs", () => {
  it("counts an ordinary tick", () => {
    expect(advanceMs(4_000, 4_250)).toBe(250);
  });

  it("counts nothing while the buffer is stalled", () => {
    // The case that makes wall-clock time the wrong measure: a panel that has
    // stopped sending would otherwise reach the grace without a frame of sound
    // ever having been possible.
    expect(advanceMs(4_000, 4_000)).toBe(0);
  });

  it("counts nothing across a seek forwards", () => {
    // Twenty minutes in one tick is somebody dragging the scrubber, not twenty
    // minutes of audio that failed to decode.
    expect(advanceMs(4_000, 1_204_000)).toBe(0);
  });

  it("counts nothing across a seek backwards", () => {
    expect(advanceMs(1_204_000, 4_000)).toBe(0);
  });
});
