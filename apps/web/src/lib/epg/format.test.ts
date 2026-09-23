import { describe, expect, it } from "vitest";
import { clockTime } from "./format";

/**
 * A time, in a zone that is named rather than inherited from the host. The
 * cases pin the two things a reader would notice: the zone (the same instant
 * is 20:05 in Paris and 18:05 in UTC) and the locale's hour cycle.
 */
describe("clockTime", () => {
  const instant = "2026-09-24T18:05:00Z";

  it("formats in the zone it is given, not the process's", () => {
    expect(clockTime(instant, "fr", "Europe/Paris")).toBe("20:05");
    expect(clockTime(instant, "fr", "UTC")).toBe("18:05");
  });

  it("writes the hour the way the locale does", () => {
    expect(clockTime(instant, "en", "Europe/Paris")).toMatch(/^8:05\s?PM$/);
  });

  it("falls back to UTC when no zone is given", () => {
    expect(clockTime(new Date(instant), "fr")).toBe("18:05");
  });

  it("returns nothing for an unreadable instant, so that nothing is drawn", () => {
    expect(clockTime("not a date", "fr", "Europe/Paris")).toBeUndefined();
  });
});
