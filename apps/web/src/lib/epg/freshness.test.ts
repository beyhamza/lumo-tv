import { describe, expect, it } from "vitest";
import type { EpgImportStatus } from "@/lib/api/types";
import { STALE_AFTER_MS, epgFreshness } from "./freshness";

/**
 * The 24-hour rule and the two clocks (C1, D3 and D4).
 *
 * The boundary is the whole point: "strictly more than 24 h" is a product
 * sentence (US-16), and `>=` would ship a guide called old at the very second
 * it turns a day.
 */

const HOUR = 60 * 60 * 1000;
const now = new Date("2026-09-25T12:00:00Z");
const iso = (date: Date) => date.toISOString();
const before = (ms: number) => new Date(now.getTime() - ms);

function succeeded(importedAt: Date): EpgImportStatus {
  return {
    configured: true,
    last_successful_import_at: iso(importedAt),
    last_attempt_started_at: iso(new Date(importedAt.getTime() - 60_000)),
    last_attempt_finished_at: iso(importedAt),
    last_attempt_status: "SUCCEEDED",
  };
}

describe("epgFreshness", () => {
  it("is unknown without a record, and not configured without a guide URL", () => {
    expect(epgFreshness(undefined, iso(now), now)).toEqual({ kind: "unknown" });
    expect(
      epgFreshness(
        {
          configured: false,
          last_successful_import_at: null,
          last_attempt_started_at: null,
          last_attempt_finished_at: null,
          last_attempt_status: "UNKNOWN",
        },
        iso(now),
        now,
      ),
    ).toEqual({ kind: "not-configured" });
  });

  it("is unknown when configured but never imported under this configuration", () => {
    expect(
      epgFreshness(
        {
          configured: true,
          last_successful_import_at: null,
          last_attempt_started_at: null,
          last_attempt_finished_at: null,
          last_attempt_status: "UNKNOWN",
        },
        iso(now),
        now,
      ),
    ).toEqual({ kind: "unknown" });
  });

  it("is fresh under 24 hours, and at 24 hours exactly", () => {
    // The answer was produced now: the whole age is the server's own arithmetic.
    expect(epgFreshness(succeeded(before(3 * HOUR)), iso(now), now)).toEqual({
      kind: "fresh",
      ageMs: 3 * HOUR,
      importedAt: before(3 * HOUR),
      unreliable: false,
    });
    expect(epgFreshness(succeeded(before(STALE_AFTER_MS)), iso(now), now).kind).toBe("fresh");
  });

  it("is stale one millisecond past 24 hours", () => {
    expect(epgFreshness(succeeded(before(STALE_AFTER_MS + 1)), iso(now), now)).toEqual({
      kind: "stale",
      ageMs: STALE_AFTER_MS + 1,
      importedAt: before(STALE_AFTER_MS + 1),
      unreliable: false,
    });
  });

  it("adds the time elapsed here since the answer was produced", () => {
    // Imported 23 h before the answer; the answer is 2 h old on this clock.
    const generatedAt = before(2 * HOUR);
    const importedAt = new Date(generatedAt.getTime() - 23 * HOUR);

    const freshness = epgFreshness(succeeded(importedAt), iso(generatedAt), now);

    expect(freshness.kind).toBe("stale");
    expect(freshness.kind === "stale" && freshness.ageMs).toBe(25 * HOUR);
  });

  it("does not rewind when this clock is behind the server's", () => {
    const generatedAt = new Date(now.getTime() + 5 * 60_000);
    const importedAt = new Date(generatedAt.getTime() - HOUR);

    const freshness = epgFreshness(succeeded(importedAt), iso(generatedAt), now);

    expect(freshness.kind === "fresh" && freshness.ageMs).toBe(HOUR);
  });

  it("flags an answer produced before the import it reports, at age zero", () => {
    const generatedAt = before(HOUR);
    const importedAt = new Date(generatedAt.getTime() + 10 * 60_000);

    expect(epgFreshness(succeeded(importedAt), iso(generatedAt), now)).toEqual({
      kind: "fresh",
      // Zero from the server's dates, plus the hour elapsed here since the answer.
      ageMs: HOUR,
      importedAt,
      unreliable: true,
    });
  });

  it("falls back to this clock for an unreadable generated_at, and says so", () => {
    const freshness = epgFreshness(succeeded(before(HOUR)), "not a date", now);

    expect(freshness).toEqual({
      kind: "fresh",
      ageMs: HOUR,
      importedAt: before(HOUR),
      unreliable: true,
    });
  });

  it("reports an attempt in trouble before any date, even a recent one", () => {
    const recent = succeeded(before(HOUR));

    expect(
      epgFreshness({ ...recent, last_attempt_status: "RUNNING" }, iso(now), now),
    ).toEqual({ kind: "attempt-running", lastImportedAt: before(HOUR) });
    expect(
      epgFreshness({ ...recent, last_attempt_status: "FAILED" }, iso(now), now),
    ).toEqual({ kind: "attempt-failed", lastImportedAt: before(HOUR) });
    expect(
      epgFreshness({ ...recent, last_attempt_status: "INTERRUPTED" }, iso(now), now),
    ).toEqual({ kind: "attempt-interrupted", lastImportedAt: before(HOUR) });
    expect(
      epgFreshness(
        { ...recent, last_attempt_status: "FAILED", last_successful_import_at: null },
        iso(now),
        now,
      ),
    ).toEqual({ kind: "attempt-failed", lastImportedAt: null });
  });

  it("treats UNKNOWN with a success date as a dated import: migrated data keeps its age", () => {
    expect(
      epgFreshness(
        { ...succeeded(before(2 * HOUR)), last_attempt_status: "UNKNOWN" },
        iso(now),
        now,
      ).kind,
    ).toBe("fresh");
  });
});
