import { describe, expect, it } from "vitest";
import { DAYS_AFTER, DAYS_BEFORE, DAY_COUNT, epgDayWindow, type EpgDay } from "./day-window";

/**
 * The five days J−1→J+3 as instants (US-16, S9-05, GD-12) — the web twin of
 * Android's `EpgDayWindowTest`.
 *
 * {@link epgDayWindow} is pure — an instant and a zone in, bounds out — so the
 * cases that go wrong on a real evening can be written down without a clock or
 * a browser: the day a programme crosses midnight, the two days a year whose
 * length is not 24 hours, the day whose midnight does not exist, and the same
 * instant read in two zones. The tests compare instants; a local date is the
 * label a screen prints, never the thing the function reasoned on.
 *
 * Times are written with their offset, exactly as the API ships them.
 */

const at = (iso: string) => new Date(iso);
const PARIS = "Europe/Paris";
const NEW_YORK = "America/New_York";
const SAO_PAULO = "America/Sao_Paulo";

/** The day of the window an instant falls in, by the same half-open rule. */
const dayOf = (window: readonly EpgDay[], instant: Date) =>
  window.find((day) => day.from.getTime() <= instant.getTime() && instant.getTime() < day.to.getTime());

const hours = (ms: number) => ms / (60 * 60 * 1000);

describe("epgDayWindow", () => {
  it("is five days centred on the local date of now", () => {
    const window = epgDayWindow(at("2026-09-24T20:00:00+02:00"), PARIS);

    expect(window.map((day) => day.date)).toEqual([
      "2026-09-23",
      "2026-09-24",
      "2026-09-25",
      "2026-09-26",
      "2026-09-27",
    ]);
    expect(window).toHaveLength(DAY_COUNT);
    expect(window[DAYS_BEFORE].date).toBe("2026-09-24");
    expect(DAYS_BEFORE + 1 + DAYS_AFTER).toBe(DAY_COUNT);
  });

  it("gives each day the calendar day, contiguous and half-open", () => {
    const window = epgDayWindow(at("2026-09-24T20:00:00+02:00"), PARIS);

    // 24 September 2026 in Paris is CEST: 00:00 local = 22:00 UTC the day before.
    expect(window[1].from.toISOString()).toBe("2026-09-23T22:00:00.000Z");
    expect(window[1].to.toISOString()).toBe("2026-09-24T22:00:00.000Z");

    window.forEach((day, index) => {
      expect(day.from.getTime()).toBeLessThan(day.to.getTime());
      // A day's end is the next one's start: no gap, no overlap.
      if (index > 0) expect(day.from.getTime()).toBe(window[index - 1].to.getTime());
    });
  });

  it("puts 23h and 00h30 in different days", () => {
    const window = epgDayWindow(at("2026-09-24T23:00:00+02:00"), PARIS);

    expect(dayOf(window, at("2026-09-24T23:00:00+02:00"))?.date).toBe("2026-09-24");
    expect(dayOf(window, at("2026-09-25T00:30:00+02:00"))?.date).toBe("2026-09-25");
  });

  it("keeps five days across spring forward, where the short day lasts 23 hours", () => {
    // Europe/Paris, 2026-03-29: 02:00 CET jumps to 03:00 CEST.
    const window = epgDayWindow(at("2026-03-29T12:00:00+02:00"), PARIS);

    expect(window.map((day) => day.date)).toEqual([
      "2026-03-28",
      "2026-03-29",
      "2026-03-30",
      "2026-03-31",
      "2026-04-01",
    ]);
    const shortened = window.find((day) => day.date === "2026-03-29");
    expect(hours(shortened!.to.getTime() - shortened!.from.getTime())).toBe(23);
    expect(hours(window[0].to.getTime() - window[0].from.getTime())).toBe(24);
    window.forEach((day, index) => {
      if (index > 0) expect(day.from.getTime()).toBe(window[index - 1].to.getTime());
    });
  });

  it("keeps five days across fall back, where the long day lasts 25 hours", () => {
    // Europe/Paris, 2026-10-25: 03:00 CEST falls back to 02:00 CET.
    const window = epgDayWindow(at("2026-10-25T12:00:00+01:00"), PARIS);

    expect(window).toHaveLength(DAY_COUNT);
    const lengthened = window.find((day) => day.date === "2026-10-25");
    expect(hours(lengthened!.to.getTime() - lengthened!.from.getTime())).toBe(25);
  });

  it("starts a skipped-midnight day at the first instant that exists", () => {
    // America/Sao_Paulo, 2018-11-04: 00:00 BRT jumps to 01:00 BRST, so the
    // day's first instant is 01:00 local (03:00 UTC), not the 00:00 that is not.
    const window = epgDayWindow(at("2018-11-04T12:00:00-02:00"), SAO_PAULO);

    const before = window.find((day) => day.date === "2018-11-03")!;
    const skipped = window.find((day) => day.date === "2018-11-04")!;
    expect(skipped.from.toISOString()).toBe("2018-11-04T03:00:00.000Z");
    expect(before.to.getTime()).toBe(skipped.from.getTime());
    expect(hours(skipped.to.getTime() - skipped.from.getTime())).toBe(23);
    expect(hours(before.to.getTime() - before.from.getTime())).toBe(24);
  });

  it("lets the zone parameter drive the window, not the machine's zone", () => {
    // One instant: 23:30 UTC is already the 25th in Paris (UTC+2) and still
    // the 24th in New York (UTC−4). Both the first date and the first instant
    // differ, so nothing defaulted to the machine's zone.
    const now = at("2026-09-24T23:30:00Z");

    const inParis = epgDayWindow(now, PARIS);
    const inNewYork = epgDayWindow(now, NEW_YORK);

    expect(inParis[0].date).toBe("2026-09-24");
    expect(inNewYork[0].date).toBe("2026-09-23");
    expect(inParis[0].from.getTime()).not.toBe(inNewYork[0].from.getTime());
  });
});
