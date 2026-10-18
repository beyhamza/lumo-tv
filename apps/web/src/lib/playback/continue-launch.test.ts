import { describe, expect, it } from "vitest";
import {
  autoplayRefused,
  launchFailureActions,
  launchQuery,
  readLaunch,
  withoutLaunch,
} from "./continue-launch";

describe("launchQuery / readLaunch", () => {
  it("round-trips what a card writes", () => {
    const query = launchQuery("resume", 754_321.7);
    expect(query).toEqual({ launch: "resume", at: "754321" });
    expect(readLaunch(query)).toEqual({ kind: "resume", atMs: 754_321 });
    expect(readLaunch(launchQuery("next", 0))).toEqual({ kind: "next", atMs: 0 });
  });

  it("writes zero for a position that is not one", () => {
    expect(launchQuery("resume", Number.NaN).at).toBe("0");
    expect(launchQuery("resume", -5).at).toBe("0");
  });

  it("is no launch for a sheet opened by hand", () => {
    expect(readLaunch({})).toBeNull();
    expect(readLaunch({ at: "1000" })).toBeNull();
    expect(readLaunch({ launch: "autoplay", at: "1000" })).toBeNull();
  });

  it("starts from zero when no position is given", () => {
    expect(readLaunch({ launch: "next" })).toEqual({ kind: "next", atMs: 0 });
  });

  it("refuses a position that is not a plain, bounded integer", () => {
    for (const at of ["-1", "1.5", "1e9", "abc", " 12", String(8 * 24 * 3600 * 1000)]) {
      expect(readLaunch({ launch: "resume", at })).toBeNull();
    }
  });

  it("reads the first value of a repeated parameter", () => {
    expect(readLaunch({ launch: ["resume", "next"], at: ["5", "9"] })).toEqual({
      kind: "resume",
      atMs: 5,
    });
  });

  it("strips the launch from a query that comes back to the sheet", () => {
    expect(withoutLaunch({ season: "2", play: "e5", launch: "next", at: "0" })).toEqual({
      season: "2",
      play: "e5",
    });
  });
});

describe("launchFailureActions", () => {
  it("keeps the card: Retry and Back home after a refused resume (CW-25)", () => {
    expect(launchFailureActions("resume", { kind: "api", code: "SOURCE_MAX_CONNECTIONS" })).toEqual(
      { retry: true, back: "home" },
    );
    expect(launchFailureActions("resume", { kind: "unplayable" })).toEqual({
      retry: true,
      back: "home",
    });
    expect(launchFailureActions("resume", { kind: "api", code: "NETWORK" }).retry).toBe(true);
  });

  it("sends a next episode that will not start back to the series (CW-27)", () => {
    expect(launchFailureActions("next", { kind: "api", code: "EPISODE_NOT_FOUND" })).toEqual({
      retry: false,
      back: "series",
    });
    expect(launchFailureActions("next", { kind: "unplayable" }).back).toBe("series");
  });

  it("offers no retry where retrying cannot help", () => {
    expect(launchFailureActions("resume", { kind: "mixed-content" }).retry).toBe(false);
    expect(launchFailureActions("resume", { kind: "api", code: "VOD_ITEM_NOT_FOUND" }).retry).toBe(
      false,
    );
  });
});

describe("autoplayRefused", () => {
  it("is the browser asking for a gesture, nothing else", () => {
    expect(autoplayRefused(new DOMException("no", "NotAllowedError"))).toBe(true);
    expect(autoplayRefused({ name: "NotAllowedError" })).toBe(true);
    expect(autoplayRefused(new DOMException("interrupted", "AbortError"))).toBe(false);
    expect(autoplayRefused(new DOMException("bad", "NotSupportedError"))).toBe(false);
    expect(autoplayRefused(undefined)).toBe(false);
    expect(autoplayRefused("NotAllowedError")).toBe(false);
  });
});
