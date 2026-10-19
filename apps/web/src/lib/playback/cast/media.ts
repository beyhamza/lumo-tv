/**
 * What the web sender tells the Chromecast, and how it reads the answer
 * (US-027, S16-02 and S16-05, ADR 0013).
 *
 * Pure: the Cast SDK is a global loaded at runtime, so everything that can be
 * decided without it is decided here and tested. The decisions come from the
 * S15-00 spike (`docs/releases/0.4.0/qa-evidence/s15-00-cast-spike-2026-10-19/`)
 * and the opening decisions of sprint 16 (`docs/backlog/sprint-16.md`).
 */

export type CastKind = "channel" | "film" | "episode";

export type CastMediaShape = {
  /** `MediaInfo.contentType`. */
  contentType: string;
  /** `MediaInfo.streamType`: a channel has no end, a file does. */
  streamType: "LIVE" | "BUFFERED";
};

/**
 * The content type, from the extension of the URL's **path**.
 *
 * No contract field says what a stream is (decision 3 of sprint 16), and Xtream
 * panels put a token after the extension, so the query is ignored. An unknown
 * extension falls back on what that kind of item usually is: HLS for a channel,
 * MP4 for a file. Matroska and raw MPEG-TS are sent as what they are and left
 * to the receiver to accept or refuse — the refusal is named either way.
 */
export function castMediaShape(url: string, kind: CastKind): CastMediaShape {
  const streamType = kind === "channel" ? "LIVE" : "BUFFERED";
  const extension = pathExtension(url);
  const contentType =
    extension === "m3u8" || extension === "m3u"
      ? "application/x-mpegurl"
      : extension === "mp4" || extension === "m4v"
        ? "video/mp4"
        : extension === "mkv"
          ? "video/x-matroska"
          : extension === "ts"
            ? "video/mp2t"
            : extension === "webm"
              ? "video/webm"
              : kind === "channel"
                ? "application/x-mpegurl"
                : "video/mp4";
  return { contentType, streamType };
}

function pathExtension(url: string): string | null {
  let path: string;
  try {
    path = new URL(url).pathname;
  } catch {
    path = url.split(/[?#]/)[0] ?? "";
  }
  const match = /\.([a-z0-9]+)$/i.exec(path);
  return match ? match[1].toLowerCase() : null;
}

/**
 * Something the sender heard about a load, in the shape this module needs.
 *
 * - `load-rejected`: the promise of `loadMedia()` was rejected, with its
 *   `chrome.cast.ErrorCode`.
 * - `media-error`: a message on the media namespace (`LOAD_FAILED`, `ERROR`,
 *   `INVALID_REQUEST`…), with its `detailedErrorCode` when there is one.
 * - `idle`: the remote player went idle, with its idle reason.
 * - `silence`: nothing played and nothing failed within [CAST_SILENCE_MS].
 */
export type CastSignal =
  | { kind: "load-rejected"; code: string | null }
  | { kind: "media-error"; type: string; detailedErrorCode: number | null }
  | { kind: "idle"; reason: string | null }
  | { kind: "silence" };

/** How long a load may go without playing or failing before it is called a refusal. */
export const CAST_SILENCE_MS = 15_000;

/**
 * Whether a signal means "this stream cannot be sent to this Chromecast".
 *
 * One answer for every refusal: the codes cannot tell CORS from an unreachable
 * host, and the acceptance criterion asks for one sentence (decision 4).
 * Not refusals: a load replaced by another (`904`, `LOAD_CANCELLED`), the
 * picker closed by the user (`cancel`), and a player that went idle because the
 * file ended or somebody stopped it.
 */
export function isCastRefusal(signal: CastSignal): boolean {
  switch (signal.kind) {
    case "load-rejected":
      return signal.code !== "cancel" && signal.code !== "LOAD_CANCELLED";
    case "media-error":
      if (signal.detailedErrorCode === 904) return false;
      return (
        signal.type === "LOAD_FAILED" ||
        signal.type === "ERROR" ||
        signal.type === "INVALID_REQUEST"
      );
    case "idle":
      return signal.reason === "ERROR";
    case "silence":
      return true;
  }
}

/** What may be written to the console about a refusal: the code, never the URL. */
export function castRefusalLog(signal: CastSignal): string {
  switch (signal.kind) {
    case "load-rejected":
      return `cast: load rejected (${signal.code ?? "unknown"})`;
    case "media-error":
      return `cast: ${signal.type} (${signal.detailedErrorCode ?? "no detailed code"})`;
    case "idle":
      return `cast: idle (${signal.reason ?? "unknown"})`;
    case "silence":
      return `cast: nothing played within ${CAST_SILENCE_MS} ms`;
  }
}
