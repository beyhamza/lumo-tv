import { describe, expect, it } from "vitest";
import { contentCounts, seriesApply } from "./content-counts";

const SYNCED = "2026-09-19T03:00:00Z";

const xtream = {
  kind: "XTREAM",
  last_synced_at: SYNCED,
  channel_count: 1248,
  category_count: 96,
} as const;

const playlist = {
  kind: "M3U_URL",
  last_synced_at: SYNCED,
  channel_count: 5,
  category_count: 3,
} as const;

/**
 * What My sources says a source holds (US-024): the numbers that are known, and
 * none of the others. An unknown value must never reach the screen as a zero.
 */
describe("contentCounts", () => {
  it("lists channels, films and series of an Xtream source", () => {
    expect(contentCounts(xtream, { films: 140_000, series: 812 })).toEqual([
      { type: "channels", count: 1248, categories: 96 },
      { type: "films", count: 140_000 },
      { type: "series", count: 812 },
    ]);
  });

  it("says nothing before a first successful import", () => {
    // Even with numbers passed in: without a catalogue they describe nothing.
    expect(
      contentCounts(
        { kind: "XTREAM", last_synced_at: null, channel_count: null, category_count: null },
        { films: 3, series: 2 },
      ),
    ).toEqual([]);
    expect(
      contentCounts({ kind: "XTREAM", last_synced_at: undefined, channel_count: 7 }),
    ).toEqual([]);
  });

  it("omits a count that failed instead of showing zero", () => {
    expect(contentCounts(xtream, { films: null, series: 812 })).toEqual([
      { type: "channels", count: 1248, categories: 96 },
      { type: "series", count: 812 },
    ]);
  });

  it("omits a count nobody asked for", () => {
    expect(contentCounts(xtream)).toEqual([
      { type: "channels", count: 1248, categories: 96 },
    ]);
  });

  it("keeps a zero the server actually counted", () => {
    // `total_elements: 0` is an answer, and "0 films" is then true.
    expect(contentCounts(xtream, { films: 0, series: 0 })).toEqual([
      { type: "channels", count: 1248, categories: 96 },
      { type: "films", count: 0 },
      { type: "series", count: 0 },
    ]);
  });

  it("never shows a series count for a playlist", () => {
    // Not even the zero its listing truthfully answers: series do not apply to
    // an M3U source (adr/0010), so the line is absent rather than "0 series".
    expect(contentCounts(playlist, { films: 12, series: 0 })).toEqual([
      { type: "channels", count: 5, categories: 3 },
      { type: "films", count: 12 },
    ]);
    expect(contentCounts({ ...playlist, kind: "M3U_FILE" }, { series: 4 })).toEqual([
      { type: "channels", count: 5, categories: 3 },
    ]);
  });

  it("keeps the channels when the categories are unknown", () => {
    expect(contentCounts({ ...xtream, category_count: null })).toEqual([
      { type: "channels", count: 1248, categories: null },
    ]);
  });

  it("omits the channels when their count is unknown", () => {
    expect(contentCounts({ ...xtream, channel_count: null }, { films: 9 })).toEqual([
      { type: "films", count: 9 },
    ]);
  });

  it("refuses values no server counted", () => {
    expect(
      contentCounts(
        { ...xtream, channel_count: -1 },
        { films: Number.NaN, series: 1.5 },
      ),
    ).toEqual([]);
  });
});

describe("seriesApply", () => {
  it("is true for Xtream only", () => {
    expect(seriesApply("XTREAM")).toBe(true);
    expect(seriesApply("M3U_URL")).toBe(false);
    expect(seriesApply("M3U_FILE")).toBe(false);
  });
});
