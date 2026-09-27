import { afterEach, describe, expect, it } from "vitest";

import { epgNow } from "./clock";

/**
 * The server-side EPG clock (I-3).
 *
 * What matters is not that an override works, but the two ways it must not:
 * an unreadable value must leave the real clock in place rather than throw or
 * move to 1970, and an unset value must be the real clock. Without those, a
 * stale `LUMO_NOW` left in an environment would make the shipped product render
 * a guide for a fixed past day.
 */
const ORIGINAL = process.env.LUMO_NOW;

afterEach(() => {
  if (ORIGINAL === undefined) {
    delete process.env.LUMO_NOW;
  } else {
    process.env.LUMO_NOW = ORIGINAL;
  }
});

describe("epgNow", () => {
  it("returns the pinned instant when LUMO_NOW is RFC 3339", () => {
    process.env.LUMO_NOW = "2026-09-26T20:00:00Z";
    expect(epgNow().toISOString()).toBe("2026-09-26T20:00:00.000Z");
  });

  it("honours the offset rather than the local zone", () => {
    process.env.LUMO_NOW = "2026-09-26T22:00:00+02:00";
    expect(epgNow().toISOString()).toBe("2026-09-26T20:00:00.000Z");
  });

  it("falls back to the real clock on an unreadable value, never 1970", () => {
    process.env.LUMO_NOW = "not a date";
    const before = Date.now();
    const at = epgNow().getTime();
    const after = Date.now();
    expect(at).toBeGreaterThanOrEqual(before);
    expect(at).toBeLessThanOrEqual(after);
  });

  it("returns the real clock when LUMO_NOW is unset", () => {
    delete process.env.LUMO_NOW;
    const before = Date.now();
    const at = epgNow().getTime();
    const after = Date.now();
    expect(at).toBeGreaterThanOrEqual(before);
    expect(at).toBeLessThanOrEqual(after);
  });
});
