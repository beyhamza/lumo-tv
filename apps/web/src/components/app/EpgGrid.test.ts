import { describe, expect, it } from "vitest";
import { epgDayWindow } from "@/lib/epg/day-window";
import { HOUR_MS, dayBlocks, dayLabel, hourMarks, type GridBlock } from "./EpgGrid";

/**
 * The grid's pure layout (S9-05-02, GD-05/06/GD-12).
 *
 * The component draws a fraction of a day; what goes wrong invisibly is that
 * fraction. So the cases here are the ones a screenshot would not reveal: a
 * programme clipped at midnight, a gap that must become one empty sentence, an
 * overlap that must not produce a negative rectangle, and the two days a year
 * whose length is not 24 hours. Positions are compared as fractions of the two
 * instants, exactly as the component places them.
 */

const PARIS = "Europe/Paris";

const programme = (id: string, title: string, starts: string, ends: string) => ({
  id,
  title,
  starts_at: starts,
  ends_at: ends,
});

/** The fraction a block starts at, for readable expectations. */
const at = (block: GridBlock) => block.left;
const width = (block: GridBlock) => block.width;

describe("hourMarks", () => {
  it("marks every hour of an ordinary day, and ends on the exclusive bound", () => {
    const from = new Date("2026-09-24T00:00:00+02:00");
    const to = new Date("2026-09-25T00:00:00+02:00");

    const marks = hourMarks(from, to);

    // 24 hours: 24 starts plus the day's end.
    expect(marks).toHaveLength(25);
    expect(marks[0].getTime()).toBe(from.getTime());
    expect(marks.at(-1)!.getTime()).toBe(to.getTime());
    marks.forEach((mark, index) => {
      expect(mark.getTime()).toBe(from.getTime() + index * HOUR_MS);
    });
  });

  it("has 23 intervals across spring forward, and 25 across fall back", () => {
    // Paris 2026-03-29: 02:00 CET → 03:00 CEST, a 23-hour day.
    const spring = epgDayWindow(new Date("2026-03-29T12:00:00+02:00"), PARIS).find(
      (day) => day.date === "2026-03-29",
    )!;
    // Paris 2026-10-25: 03:00 CEST → 02:00 CET, a 25-hour day.
    const autumn = epgDayWindow(new Date("2026-10-25T12:00:00+01:00"), PARIS).find(
      (day) => day.date === "2026-10-25",
    )!;

    expect(hourMarks(spring.from, spring.to)).toHaveLength(24);
    expect(hourMarks(autumn.from, autumn.to)).toHaveLength(26);

    // Each mark is one fixed hour after the previous one, never a rebuilt
    // calendar hour — that is what keeps the columns honest on those days.
    for (const marks of [hourMarks(spring.from, spring.to), hourMarks(autumn.from, autumn.to)]) {
      marks.forEach((mark, index) => {
        if (index > 0) expect(mark.getTime() - marks[index - 1].getTime()).toBe(HOUR_MS);
      });
    }
  });
});

describe("dayBlocks", () => {
  const from = new Date("2026-09-24T00:00:00+02:00");
  const to = new Date("2026-09-25T00:00:00+02:00");

  it("places a programme by its instants, half-open at both bounds", () => {
    const blocks = dayBlocks(
      [programme("a", "Journal", "2026-09-24T20:00:00+02:00", "2026-09-24T20:30:00+02:00")],
      from,
      to,
    );

    expect(blocks).toHaveLength(3);
    const midday = blocks[1];
    expect(midday.empty).toBe(false);
    expect(midday.title).toBe("Journal");
    // 20:00 in a day that starts at 00:00 is 20/24 of the way.
    expect(at(midday)).toBeCloseTo(20 / 24);
    expect(width(midday)).toBeCloseTo(0.5 / 24);
  });

  it("clips a programme that crosses midnight and drops one that ends exactly at the bound", () => {
    const blocks = dayBlocks(
      [
        programme("yesterday", "Nuit", "2026-09-23T23:00:00+02:00", "2026-09-24T01:00:00+02:00"),
        programme("tomorrow", "Matin", "2026-09-25T00:00:00+02:00", "2026-09-25T02:00:00+02:00"),
        programme("ends-at-midnight", "Fin", "2026-09-23T22:00:00+02:00", "2026-09-24T00:00:00+02:00"),
      ],
      from,
      to,
    );

    const titles = blocks.map((block) => block.title).filter(Boolean);
    expect(titles).toEqual(["Nuit"]);
    const nuit = blocks.find((block) => block.title === "Nuit")!;
    expect(at(nuit)).toBe(0);
    expect(width(nuit)).toBeCloseTo(1 / 24);
    // A programme starting exactly at `to` belongs to the next day, not this one.
    expect(titles).not.toContain("Matin");
    // A programme ending exactly at `from` belongs to the previous day.
    expect(titles).not.toContain("Fin");
  });

  it("fills a gap with one empty block, with leading and trailing ones", () => {
    const blocks = dayBlocks(
      [
        programme("a", "A", "2026-09-24T10:00:00+02:00", "2026-09-24T11:00:00+02:00"),
        programme("b", "B", "2026-09-24T13:00:00+02:00", "2026-09-24T14:00:00+02:00"),
      ],
      from,
      to,
    );

    expect(blocks.map((block) => block.empty)).toEqual([true, false, true, false, true]);
    const gap = blocks[2];
    expect(at(gap)).toBeCloseTo(11 / 24);
    expect(width(gap)).toBeCloseTo(2 / 24);
    expect(blocks[0].startsAt).toBe(from.getTime());
    expect(blocks.at(-1)!.endsAt).toBe(to.getTime());
  });

  it("treats a day with no programme as one empty slot, not as nothing", () => {
    const blocks = dayBlocks([], from, to);

    expect(blocks).toHaveLength(1);
    expect(blocks[0].empty).toBe(true);
    expect(at(blocks[0])).toBe(0);
    expect(width(blocks[0])).toBe(1);
    expect(blocks[0].startsAt).toBe(from.getTime());
    expect(blocks[0].endsAt).toBe(to.getTime());
  });

  it("does not draw a negative rectangle when listings overlap", () => {
    const blocks = dayBlocks(
      [
        programme("a", "Long", "2026-09-24T10:00:00+02:00", "2026-09-24T12:00:00+02:00"),
        programme("b", "Overlap", "2026-09-24T11:00:00+02:00", "2026-09-24T11:30:00+02:00"),
      ],
      from,
      to,
    );

    const placed = blocks.filter((block) => !block.empty);
    expect(placed.map((block) => block.title)).toEqual(["Long"]);
    for (const block of blocks) {
      if (block.empty) continue;
      expect(block.width).toBeGreaterThan(0);
      expect(block.endsAt).toBeGreaterThan(block.startsAt);
    }
  });

  it("skips a programme whose time cannot be read, and still draws the rest", () => {
    const blocks = dayBlocks(
      [
        programme("bad", "Cassé", "pas-une-date", "2026-09-24T10:00:00+02:00"),
        programme("good", "Bon", "2026-09-24T10:00:00+02:00", "2026-09-24T11:00:00+02:00"),
      ],
      from,
      to,
    );

    expect(blocks.map((block) => block.title).filter(Boolean)).toEqual(["Bon"]);
  });
});

describe("dayLabel", () => {
  it("prints the calendar day in UTC, whatever the machine's zone", () => {
    // 1 January in UTC is still 31 December in the Americas; formatting in UTC
    // keeps the label on the day the window named.
    const label = dayLabel("2026-01-01", "fr-FR");

    expect(label).toContain("janv");
    expect(label).toContain("1");
  });
});
