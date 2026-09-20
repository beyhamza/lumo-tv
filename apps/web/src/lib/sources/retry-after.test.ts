import { describe, expect, it } from "vitest";
import {
  MAX_WAIT_SECONDS,
  displayedWait,
  parseRetryAfter,
  remainingWaitSeconds,
  retryAtParam,
  syncLimitNotice,
} from "./retry-after";

/** 20 September 2026, 12:00:00.400 UTC — deliberately not on a whole second. */
const NOW_MS = 1_789_905_600_400;
const NOW_S = 1_789_905_600;

/**
 * The server's wait between two manual refreshes (US-024, C4 — P5).
 *
 * Every refusal below ends in `null`, and `null` is rendered as the message
 * without a number: this client never invents a duration.
 */
describe("parseRetryAfter", () => {
  it("reads the seconds the contract promises", () => {
    expect(parseRetryAfter("300")).toBe(300);
    expect(parseRetryAfter(" 42 ")).toBe(42);
    expect(parseRetryAfter("1")).toBe(1);
  });

  it("answers null when the header is missing", () => {
    expect(parseRetryAfter(null)).toBeNull();
    expect(parseRetryAfter(undefined)).toBeNull();
    expect(parseRetryAfter("")).toBeNull();
  });

  it("refuses anything that is not plain digits", () => {
    // Each of these parses to a number with a laxer reader, and none of them is
    // what a server following the contract sends.
    for (const header of ["-5", "+5", "1.5", "1e3", "12abc", "0x10", "five", "3 00"]) {
      expect(parseRetryAfter(header)).toBeNull();
    }
  });

  it("does not parse the HTTP-date form, which the contract does not use", () => {
    expect(parseRetryAfter("Sun, 20 Sep 2026 12:05:00 GMT")).toBeNull();
  });

  it("refuses zero and anything beyond a day", () => {
    expect(parseRetryAfter("0")).toBeNull();
    expect(parseRetryAfter(String(MAX_WAIT_SECONDS))).toBe(MAX_WAIT_SECONDS);
    expect(parseRetryAfter(String(MAX_WAIT_SECONDS + 1))).toBeNull();
    expect(parseRetryAfter("999999999")).toBeNull();
  });
});

describe("the retryAt parameter", () => {
  it("round-trips a wait through the URL", () => {
    const param = retryAtParam(300, NOW_MS);
    expect(param).toBe(String(NOW_S + 300));
    // Exactly what the server said — not 301, which would display as 6 minutes.
    expect(remainingWaitSeconds(param, NOW_MS)).toBe(300);
  });

  it("counts down as time passes, and expires by itself", () => {
    const param = retryAtParam(300, NOW_MS);
    expect(remainingWaitSeconds(param, NOW_MS + 120_000)).toBe(180);
    // The bookmark opened the next day says nothing.
    expect(remainingWaitSeconds(param, NOW_MS + 300_000)).toBeNull();
    expect(remainingWaitSeconds(param, NOW_MS + 86_400_000)).toBeNull();
  });

  it("accepts digits only", () => {
    for (const value of ["", "abc", "-1", "1.5", "1e12", `${NOW_S + 60}x`, " 1", "1789905660 "]) {
      expect(remainingWaitSeconds(value, NOW_MS)).toBeNull();
    }
  });

  it("accepts a single string only", () => {
    // `searchParams` hands back an array for a repeated key.
    expect(remainingWaitSeconds([String(NOW_S + 60)], NOW_MS)).toBeNull();
    expect(remainingWaitSeconds(undefined, NOW_MS)).toBeNull();
    expect(remainingWaitSeconds(NOW_S + 60, NOW_MS)).toBeNull();
  });

  it("refuses an instant further away than any wait this client writes", () => {
    expect(remainingWaitSeconds(String(NOW_S + MAX_WAIT_SECONDS), NOW_MS)).toBe(
      MAX_WAIT_SECONDS,
    );
    expect(remainingWaitSeconds(String(NOW_S + MAX_WAIT_SECONDS + 1), NOW_MS)).toBeNull();
    expect(remainingWaitSeconds("999999999999", NOW_MS)).toBeNull();
  });
});

describe("displayedWait", () => {
  it("says under a minute rather than zero minutes", () => {
    expect(displayedWait(1)).toEqual({ unit: "under-a-minute" });
    expect(displayedWait(59)).toEqual({ unit: "under-a-minute" });
  });

  it("rounds minutes up, never down", () => {
    expect(displayedWait(60)).toEqual({ unit: "minutes", count: 1 });
    expect(displayedWait(61)).toEqual({ unit: "minutes", count: 2 });
    // 2 min 10 s: "2 min" would send somebody back ten seconds too early.
    expect(displayedWait(130)).toEqual({ unit: "minutes", count: 3 });
    expect(displayedWait(170)).toEqual({ unit: "minutes", count: 3 });
    expect(displayedWait(300)).toEqual({ unit: "minutes", count: 5 });
    expect(displayedWait(3600)).toEqual({ unit: "minutes", count: 60 });
  });

  it("switches to hours beyond an hour", () => {
    expect(displayedWait(3601)).toEqual({ unit: "hours", count: 2 });
    expect(displayedWait(MAX_WAIT_SECONDS)).toEqual({ unit: "hours", count: 24 });
  });
});

describe("syncLimitNotice", () => {
  it("says nothing unless the action reported a limit", () => {
    expect(syncLimitNotice(undefined, undefined, NOW_MS)).toBeNull();
    expect(syncLimitNotice("failed", String(NOW_S + 60), NOW_MS)).toBeNull();
    expect(syncLimitNotice(["limited"], undefined, NOW_MS)).toBeNull();
  });

  it("gives the message without a number when the server named no delay", () => {
    expect(syncLimitNotice("limited", undefined, NOW_MS)).toEqual({ wait: null });
  });

  it("gives the time left when the server named one", () => {
    expect(syncLimitNotice("limited", retryAtParam(300, NOW_MS), NOW_MS)).toEqual({
      // A five-minute limit reads "about 5 min", not 6.
      wait: { unit: "minutes", count: 5 },
    });
    expect(syncLimitNotice("limited", retryAtParam(300, NOW_MS), NOW_MS + 250_000)).toEqual({
      wait: { unit: "under-a-minute" },
    });
  });

  it("says nothing once the wait is over, or when retryAt was tampered with", () => {
    const param = retryAtParam(300, NOW_MS);
    expect(syncLimitNotice("limited", param, NOW_MS + 600_000)).toBeNull();
    expect(syncLimitNotice("limited", "soon", NOW_MS)).toBeNull();
    expect(syncLimitNotice("limited", "999999999999", NOW_MS)).toBeNull();
    expect(syncLimitNotice("limited", [param], NOW_MS)).toBeNull();
  });
});
