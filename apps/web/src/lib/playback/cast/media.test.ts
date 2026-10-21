import { describe, expect, it } from "vitest";
import { castMediaShape, castRefusalLog, isCastRefusal } from "./media";

describe("castMediaShape", () => {
  it("reads the extension of the path, never of the query", () => {
    expect(castMediaShape("http://panel.test/movie/u/p/42.mp4?token=a.m3u8", "film")).toEqual({
      contentType: "video/mp4",
      streamType: "BUFFERED",
    });
    expect(castMediaShape("http://panel.test/live/u/p/7.m3u8", "channel")).toEqual({
      contentType: "application/x-mpegurl",
      streamType: "LIVE",
    });
  });

  it("sends Matroska and raw MPEG-TS as what they are", () => {
    expect(castMediaShape("http://panel.test/series/u/p/9.mkv", "episode").contentType).toBe(
      "video/x-matroska",
    );
    expect(castMediaShape("http://panel.test/live/u/p/7.ts", "channel").contentType).toBe(
      "video/mp2t",
    );
  });

  it("falls back on what the kind usually is", () => {
    expect(castMediaShape("http://panel.test/play/7", "channel").contentType).toBe(
      "application/x-mpegurl",
    );
    expect(castMediaShape("http://panel.test/play/42", "film").contentType).toBe("video/mp4");
  });

  it("does not throw on a URL it cannot parse", () => {
    expect(castMediaShape("not a url.mkv?x", "film").contentType).toBe("video/x-matroska");
  });
});

describe("isCastRefusal", () => {
  it("names a rejected load, a load error, an idle on error and silence", () => {
    expect(isCastRefusal({ kind: "load-rejected", code: "session_error" })).toBe(true);
    expect(isCastRefusal({ kind: "media-error", type: "LOAD_FAILED", detailedErrorCode: 104 })).toBe(
      true,
    );
    expect(isCastRefusal({ kind: "idle", reason: "ERROR" })).toBe(true);
    expect(isCastRefusal({ kind: "silence" })).toBe(true);
  });

  it("ignores a replaced load, a closed picker and a normal end", () => {
    expect(isCastRefusal({ kind: "load-rejected", code: "cancel" })).toBe(false);
    expect(isCastRefusal({ kind: "load-rejected", code: "LOAD_CANCELLED" })).toBe(false);
    expect(isCastRefusal({ kind: "media-error", type: "ERROR", detailedErrorCode: 904 })).toBe(false);
    expect(isCastRefusal({ kind: "idle", reason: "FINISHED" })).toBe(false);
    expect(isCastRefusal({ kind: "idle", reason: "CANCELLED" })).toBe(false);
  });
});

describe("castRefusalLog", () => {
  it("carries the code and nothing else", () => {
    expect(castRefusalLog({ kind: "media-error", type: "LOAD_FAILED", detailedErrorCode: 3116404 })).toBe(
      "cast: LOAD_FAILED (3116404)",
    );
  });
});
