import { describe, expect, it } from "vitest";
import type { EpgChannelProgrammes, EpgProgramme } from "@/lib/api/types";
import {
  findProgrammeInGrid,
  momentOf,
  nextTransitionAt,
  watchAllowed,
  watchAvailable,
} from "./programme";

/**
 * The programme sheet's temporal rule, web side (S9-06-04, GD-07/08).
 *
 * These are the Android `momentOf` / `watchAvailable` / `watchAllowed` rules
 * (S9-06-01, `ProgrammeSheet.kt`) with the same half-open interval: current
 * from `starts_at` **inclusive** to `ends_at` **exclusive**. They are pure and
 * take the instant as an argument, so the sheet can re-read `now` at activation
 * without a second definition drifting from the one the panel draws.
 */

const programme = (
  id: string,
  starts: string,
  ends: string,
  title = "Journal",
): EpgProgramme => ({
  id,
  source_id: "11111111-1111-4111-8111-111111111111",
  tvg_id: "bench.1",
  starts_at: starts,
  ends_at: ends,
  title,
});

const A = programme("aaa", "2026-09-26T20:00:00Z", "2026-09-26T21:00:00Z");

describe("momentOf", () => {
  it("is future before the start, current from the start, past from the end", () => {
    expect(momentOf(A, new Date("2026-09-26T19:59:59Z"))).toBe("future");
    // Start is inclusive: at 20:00 exactly the programme is on.
    expect(momentOf(A, new Date("2026-09-26T20:00:00Z"))).toBe("current");
    expect(momentOf(A, new Date("2026-09-26T20:59:59Z"))).toBe("current");
    // End is exclusive: one millisecond later it is over.
    expect(momentOf(A, new Date("2026-09-26T21:00:00Z"))).toBe("past");
  });

  it("accepts an instant as a number of milliseconds too", () => {
    expect(momentOf(A, Date.parse("2026-09-26T20:30:00Z"))).toBe("current");
  });

  it("never marks a zero-length programme current (QA-06-L01)", () => {
    const zero = programme("zero", "2026-09-26T20:00:00Z", "2026-09-26T20:00:00Z");
    expect(momentOf(zero, new Date("2026-09-26T20:00:00Z"))).toBe("past");
  });

  it("refuses the action, rather than throwing, on an unreadable instant", () => {
    const broken = programme("bad", "pas-une-date", "2026-09-26T21:00:00Z");
    expect(momentOf(broken, new Date("2026-09-26T20:00:00Z"))).toBe("past");
  });
});

describe("watchAvailable", () => {
  it("offers the action for a current programme and no other", () => {
    expect(watchAvailable("current")).toBe(true);
    expect(watchAvailable("future")).toBe(false);
    expect(watchAvailable("past")).toBe(false);
  });
});

describe("watchAllowed — GD-08, the instant is read again at activation", () => {
  it("allows a current programme and refuses a past one", () => {
    expect(watchAllowed(A, new Date("2026-09-26T20:30:00Z"))).toBe(true);
    expect(watchAllowed(A, new Date("2026-09-26T21:00:00Z"))).toBe(false);
  });

  it("refuses even though the panel drew the action for that same programme", () => {
    // The sheet was rendered while current; the press happens after the end.
    const drawnAt = new Date("2026-09-26T20:59:00Z");
    const pressedAt = new Date("2026-09-26T21:00:01Z");
    expect(watchAvailable(momentOf(A, drawnAt))).toBe(true);
    expect(watchAllowed(A, pressedAt)).toBe(false);
  });
});

describe("nextTransitionAt — the one instant that wakes the client clock", () => {
  it("returns the start for a future programme and the end for a current one", () => {
    expect(nextTransitionAt(A, new Date("2026-09-26T19:00:00Z"))).toBe(
      Date.parse("2026-09-26T20:00:00Z"),
    );
    expect(nextTransitionAt(A, new Date("2026-09-26T20:30:00Z"))).toBe(
      Date.parse("2026-09-26T21:00:00Z"),
    );
  });

  it("returns nothing once the programme is past, or unreadable", () => {
    expect(nextTransitionAt(A, new Date("2026-09-26T21:00:00Z"))).toBeUndefined();
    expect(
      nextTransitionAt(programme("bad", "pas-une-date", "2026-09-26T21:00:00Z"), Date.now()),
    ).toBeUndefined();
  });
});

describe("findProgrammeInGrid — resolving `?programme=`", () => {
  const channels: EpgChannelProgrammes[] = [
    { channel_id: "ch-1", mapping_status: "MAPPED", programmes: [A] },
    {
      channel_id: "ch-2",
      mapping_status: "MAPPED",
      programmes: [programme("bbb", "2026-09-26T21:00:00Z", "2026-09-26T22:00:00Z", "Film")],
    },
  ];

  it("returns the programme and the channel that carries it", () => {
    const found = findProgrammeInGrid(channels, "bbb");
    expect(found?.channel.channel_id).toBe("ch-2");
    expect(found?.programme.title).toBe("Film");
  });

  it("returns nothing for an identifier that is not in the window", () => {
    expect(findProgrammeInGrid(channels, "absent")).toBeUndefined();
  });
});
