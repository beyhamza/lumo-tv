/**
 * Picture-in-Picture on the web players (US-026, S15-03, ADR 0013).
 *
 * The browser API is the whole feature: the `<video>` keeps playing in a
 * floating window, so progress, watched time and the pause save go on through
 * the element's own events, unchanged. What this module decides is only
 * whether to offer the button at all — a browser without the API, or one where
 * the user or a policy turned it off, gets no button rather than a button that
 * fails.
 */

/** The parts of `document` this reads, so a test can hand in a plain object. */
export type PipDocument = {
  pictureInPictureEnabled?: boolean;
  pictureInPictureElement?: Element | null;
  exitPictureInPicture?: () => Promise<void>;
};

/** The parts of the `<video>` this reads. */
export type PipVideo = {
  disablePictureInPicture?: boolean;
  requestPictureInPicture?: () => Promise<unknown>;
};

/** Whether the button is shown for this element in this browser. */
export function pipAvailable(doc: PipDocument, video: PipVideo | null): boolean {
  if (!video) return false;
  return (
    doc.pictureInPictureEnabled === true &&
    typeof video.requestPictureInPicture === "function" &&
    typeof doc.exitPictureInPicture === "function" &&
    video.disablePictureInPicture !== true
  );
}

/**
 * Enters the window for `video`, or leaves it when `video` is the one in it.
 * Resolves to whether the request went through; a refusal (no user gesture,
 * metadata not loaded yet) is reported, never thrown.
 */
export async function togglePip(doc: PipDocument, video: PipVideo & Element): Promise<boolean> {
  try {
    if (doc.pictureInPictureElement === video) await doc.exitPictureInPicture?.();
    else await video.requestPictureInPicture?.();
    return true;
  } catch {
    return false;
  }
}
