import { describe, expect, it } from "vitest";
import { currentAndNext, onAirByChannel } from "./now";

/**
 * "On now" from instants. Each case is a listing shape a provider actually
 * ships: back to back, with a gap, only the past, only the future, across
 * midnight — and one with a time that cannot be read.
 */

const at = (iso: string) => new Date(iso);
const p = (id: string, startsAt: string, endsAt: string) => ({
  id,
  starts_at: startsAt,
  ends_at: endsAt,
});

describe("currentAndNext", () => {
  it("picks the programme on air and the one after it", () => {
    const programmes = [
      p("a", "2026-09-24T18:00:00Z", "2026-09-24T19:00:00Z"),
      p("b", "2026-09-24T19:00:00Z", "2026-09-24T20:00:00Z"),
      p("c", "2026-09-24T20:00:00Z", "2026-09-24T21:00:00Z"),
    ];

    const { current, next } = currentAndNext(programmes, at("2026-09-24T19:30:00Z"));

    expect(current?.id).toBe("b");
    expect(next?.id).toBe("c");
  });

  it("is on the next programme at the very instant one ends", () => {
    const programmes = [
      p("a", "2026-09-24T18:00:00Z", "2026-09-24T19:00:00Z"),
      p("b", "2026-09-24T19:00:00Z", "2026-09-24T20:00:00Z"),
    ];

    const { current, next } = currentAndNext(programmes, at("2026-09-24T19:00:00Z"));

    expect(current?.id).toBe("b");
    expect(next).toBeUndefined();
  });

  it("has nothing on air in a gap, and still knows what is coming", () => {
    const programmes = [
      p("a", "2026-09-24T18:00:00Z", "2026-09-24T18:30:00Z"),
      p("b", "2026-09-24T19:00:00Z", "2026-09-24T20:00:00Z"),
    ];

    const { current, next } = currentAndNext(programmes, at("2026-09-24T18:45:00Z"));

    expect(current).toBeUndefined();
    expect(next?.id).toBe("b");
  });

  it("has nothing when every programme is over", () => {
    const programmes = [p("a", "2026-09-24T18:00:00Z", "2026-09-24T19:00:00Z")];

    expect(currentAndNext(programmes, at("2026-09-24T22:00:00Z"))).toEqual({
      current: undefined,
      next: undefined,
    });
  });

  it("has only a next when every programme is still to come", () => {
    const programmes = [
      p("b", "2026-09-24T20:00:00Z", "2026-09-24T21:00:00Z"),
      p("a", "2026-09-24T19:00:00Z", "2026-09-24T20:00:00Z"),
    ];

    const { current, next } = currentAndNext(programmes, at("2026-09-24T18:00:00Z"));

    expect(current).toBeUndefined();
    // The earliest, not the first listed: the order is restored, never trusted.
    expect(next?.id).toBe("a");
  });

  it("crosses midnight as instants, with offsets", () => {
    const programmes = [
      // 23:30–00:30 Paris time, written with its offset as the API does.
      p("night", "2026-09-24T23:30:00+02:00", "2026-09-25T00:30:00+02:00"),
      p("morning", "2026-09-25T00:30:00+02:00", "2026-09-25T06:00:00+02:00"),
    ];

    // 00:10 Paris = 22:10 UTC the day before.
    const { current, next } = currentAndNext(programmes, at("2026-09-24T22:10:00Z"));

    expect(current?.id).toBe("night");
    expect(next?.id).toBe("morning");
  });

  it("ignores a programme whose time cannot be read, and places the others", () => {
    const programmes = [
      p("broken", "yesterday", "2026-09-24T19:00:00Z"),
      p("a", "2026-09-24T18:00:00Z", "2026-09-24T19:00:00Z"),
    ];

    expect(currentAndNext(programmes, at("2026-09-24T18:30:00Z")).current?.id).toBe("a");
  });

  it("is empty for an empty list", () => {
    expect(currentAndNext([], at("2026-09-24T18:30:00Z"))).toEqual({
      current: undefined,
      next: undefined,
    });
  });
});

describe("onAirByChannel", () => {
  it("maps only the channels with something on: the others are absent, not empty", () => {
    const programme = {
      id: "a",
      source_id: "s",
      tvg_id: "t",
      starts_at: "2026-09-24T18:00:00Z",
      ends_at: "2026-09-24T19:00:00Z",
      title: "On air",
    };
    const rows = [
      { channel_id: "with", mapping_status: "MAPPED" as const, programmes: [programme] },
      { channel_id: "without", mapping_status: "NO_TVG_ID" as const, programmes: [] },
    ];

    const onAir = onAirByChannel(rows, at("2026-09-24T18:30:00Z"));

    expect([...onAir.keys()]).toEqual(["with"]);
    expect(onAir.get("with")?.title).toBe("On air");
  });
});
