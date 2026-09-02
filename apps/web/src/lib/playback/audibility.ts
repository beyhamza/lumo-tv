/**
 * Whether a browser is getting any sound out of what it is playing.
 *
 * <h2>Why this needs deciding at all</h2>
 *
 * A panel serves what it has, and a third of a real catalogue carries **Dolby
 * Digital** — AC-3 or E-AC-3 — as its only audio. Chrome, Edge and Firefox ship
 * no decoder for either: Dolby is licensed and Chromium does not pay it. So the
 * browser decodes the H.264 picture perfectly, finds no audio track it can use,
 * and puts its own volume control in the muted state.
 *
 * That last detail is what makes this worth code. The person watching sees a
 * mute button that looks pressed, presses it, and nothing happens — because
 * there is nothing to unmute. Found on a real catalogue: a series played, the
 * picture was fine, and the report was *"I don't know why I have no sound, it is
 * always muted."* Nothing on the page said a word.
 *
 * <h2>Three browsers, three answers, none of them the standard one</h2>
 *
 * `HTMLMediaElement.audioTracks` is the specified way to ask, and **Chrome does
 * not implement it** — the one browser where the problem is most common. So the
 * reading is whatever each engine actually offers:
 *
 * <ul>
 *   <li>`audioTracks.length` — Safari, and Firefox behind a pref. Authoritative
 *       as soon as metadata is in.</li>
 *   <li>`mozHasAudio` — Firefox. Same, and older.</li>
 *   <li>`webkitAudioDecodedByteCount` — Chromium and WebKit. Not a declaration
 *       but a count, so it says nothing until decoding has actually run.</li>
 * </ul>
 *
 * @see audibility for what is done with the three.
 */
export type Audibility = "audible" | "silent" | "unknown";

/**
 * The verdict on one reading of a media element.
 *
 * <p><b>"unknown" is a first-class answer and most of this function's caution
 * lives in it.</b> The line this feeds says the browser cannot play the sound at
 * all, and saying that wrongly sends somebody to install an application over a
 * problem they do not have. Silence is only claimed on evidence; everything else
 * stays quiet, which is what the page did before this existed.
 *
 * <p><b>A user's own mute is never called silence.</b> `muted` is not set by a
 * missing track — the element stays unmuted and only the control *renders* as
 * muted — so a `true` here is somebody's finger, and telling them their codec is
 * unsupported would be the page arguing with them.
 *
 * <p><b>The byte count needs played time, not wall-clock time.</b> Zero bytes
 * decoded means nothing while a file is still opening, or while a buffer is
 * stalled on a slow panel. So the grace is measured in media that actually went
 * past — see {@link SILENCE_AFTER_MS}.
 */
export function audibility(reading: {
  /** `audioTracks.length`, where the browser has it. */
  audioTrackCount?: number;
  /** `mozHasAudio`, where the browser has it. */
  hasAudio?: boolean;
  /** `webkitAudioDecodedByteCount`, where the browser has it. */
  audioBytesDecoded?: number;
  /** Media time that has genuinely played, accumulated — not time on a clock. */
  playedMs: number;
  /** `video.muted`. Somebody's own choice, and it ends the question. */
  muted: boolean;
}): Audibility {
  if (reading.muted) return "unknown";

  // The declarations first, in the order of how much they know. Both are true
  // the moment metadata is in, which the count below can never be.
  if (typeof reading.audioTrackCount === "number") {
    return reading.audioTrackCount > 0 ? "audible" : "silent";
  }
  if (typeof reading.hasAudio === "boolean") {
    return reading.hasAudio ? "audible" : "silent";
  }

  if (typeof reading.audioBytesDecoded === "number") {
    if (reading.audioBytesDecoded > 0) return "audible";
    // Nothing decoded — which is either no decoder, or a file that has not got
    // there yet. Only the second one is fixed by waiting.
    return reading.playedMs >= SILENCE_AFTER_MS ? "silent" : "unknown";
  }

  // A browser that offers none of the three. It may well be playing sound; we
  // simply have no way to ask, and guessing would be worse than saying nothing.
  return "unknown";
}

/**
 * How much media must have played before zero decoded bytes means anything.
 *
 * <p>Three seconds. A browser decodes audio ahead of the picture, so a stream
 * with sound crosses zero within the first frames and never comes near this —
 * the delay only ever costs the silent case, where three seconds of wondering is
 * roughly what somebody spends reaching for the volume anyway.
 */
export const SILENCE_AFTER_MS = 3_000;

/**
 * Media time that genuinely advanced between two `timeupdate` readings.
 *
 * <p>Not `now - then`: a stalled buffer would count as played and declare
 * silence over a file that has not been heard yet. And not a raw difference in
 * `currentTime` either, because a seek moves it by minutes in one tick — those
 * are dropped rather than added, which is why this caps rather than clamps.
 */
export function advanceMs(previousMs: number, currentMs: number): number {
  const delta = currentMs - previousMs;
  return delta > 0 && delta <= MAX_TICK_MS ? delta : 0;
}

/**
 * The largest gap between two readings that can be ordinary playing.
 *
 * <p>`timeupdate` fires about four times a second, so two seconds is many missed
 * ticks — a tab in the background, or the end of a seek. Either way it is not
 * evidence of anything having been heard.
 */
const MAX_TICK_MS = 2_000;
